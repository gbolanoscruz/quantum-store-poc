package com.quantumstore.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    TokenService tokenService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "quantum.auth.google-client-id")
    String expectedGoogleClientId;

    @POST
    @Path("/register")
    @Transactional
    public Response register(@Valid RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (UserAccount.findByEmailNormalized(normalizedEmail) != null) {
            meterRegistry.counter("quantum_auth_total", "flow", "register", "outcome", "conflict").increment();
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"Email is already registered\"}")
                    .build();
        }

        UserAccount user = new UserAccount();
        user.email = normalizedEmail;
        user.fullName = request.fullName().trim();
        user.passwordHash = passwordHasher.hash(request.password());
        user.authProvider = AuthProvider.LOCAL;
        user.role = "CUSTOMER";
        user.active = true;
        user.persist();

        String jwt = tokenService.issueToken(user);
        meterRegistry.counter("quantum_auth_total", "flow", "register", "outcome", "success").increment();
        Log.infof("User registered id=%d email=%s provider=LOCAL", user.id, user.email);

        return Response.created(URI.create("/api/auth/me"))
                .entity(new AuthResponse(jwt, "Bearer", tokenService.getTtlSeconds(), UserProfileResponse.from(user)))
                .build();
    }

    @POST
    @Path("/login")
    @Transactional
    public AuthResponse login(@Valid LoginRequest request) {
        UserAccount user = UserAccount.findByEmailNormalized(request.email());
        if (user == null || !user.active || user.passwordHash == null
                || !passwordHasher.verify(request.password(), user.passwordHash)) {
            meterRegistry.counter("quantum_auth_total", "flow", "login_local", "outcome", "invalid").increment();
            throw new NotAuthorizedException("Invalid email or password");
        }

        String jwt = tokenService.issueToken(user);
        meterRegistry.counter("quantum_auth_total", "flow", "login_local", "outcome", "success").increment();
        Log.infof("User logged in id=%d provider=LOCAL", user.id);

        return new AuthResponse(jwt, "Bearer", tokenService.getTtlSeconds(), UserProfileResponse.from(user));
    }

    @POST
    @Path("/google")
    @Transactional
    public AuthResponse loginWithGoogle(@Valid GoogleLoginRequest request) {
        JsonNode tokenInfo = verifyGoogleIdToken(request.idToken());
        String email = tokenInfo.path("email").asText("").trim().toLowerCase();
        boolean emailVerified = "true".equalsIgnoreCase(tokenInfo.path("email_verified").asText("false"));
        String sub = tokenInfo.path("sub").asText("").trim();
        String name = tokenInfo.path("name").asText("");
        if (name.isBlank()) {
            name = email.contains("@") ? email.substring(0, email.indexOf('@')) : "Google User";
        }

        if (email.isBlank() || sub.isBlank() || !emailVerified) {
            meterRegistry.counter("quantum_auth_total", "flow", "login_google", "outcome", "invalid").increment();
            throw new NotAuthorizedException("Unverified or invalid Google token");
        }

        if (expectedGoogleClientId != null && !expectedGoogleClientId.isBlank()) {
            String aud = tokenInfo.path("aud").asText("");
            if (!expectedGoogleClientId.equals(aud)) {
                meterRegistry.counter("quantum_auth_total", "flow", "login_google", "outcome", "audience_mismatch").increment();
                throw new NotAuthorizedException("Google token audience mismatch");
            }
        }

        UserAccount user = UserAccount.find("googleSub", sub).firstResult();
        if (user == null) {
            user = UserAccount.findByEmailNormalized(email);
        }

        if (user == null) {
            user = new UserAccount();
            user.email = email;
            user.fullName = name;
            user.authProvider = AuthProvider.GOOGLE;
            user.googleSub = sub;
            user.role = "CUSTOMER";
            user.active = true;
            user.persist();
            Log.infof("New Google user provisioned id=%d provider=GOOGLE", user.id);
        } else {
            if (!user.active) {
                throw new NotAuthorizedException("Account is inactive");
            }
            if (user.googleSub == null) {
                user.googleSub = sub;
            }
            if (name != null && !name.isBlank()) {
                user.fullName = name;
            }
        }

        String jwt = tokenService.issueToken(user);
        meterRegistry.counter("quantum_auth_total", "flow", "login_google", "outcome", "success").increment();
        Log.infof("User logged in id=%d provider=GOOGLE", user.id);

        return new AuthResponse(jwt, "Bearer", tokenService.getTtlSeconds(), UserProfileResponse.from(user));
    }

    @GET
    @Path("/me")
    public UserProfileResponse me(@HeaderParam("Authorization") String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new NotAuthorizedException("Missing Bearer token");
        }
        String rawToken = authorizationHeader.substring("Bearer ".length()).trim();
        JsonNode claims = tokenService.parseAndValidate(rawToken);
        if (claims == null) {
            throw new NotAuthorizedException("Invalid or expired token");
        }

        String email = claims.path("email").asText("");
        UserAccount user = UserAccount.findByEmailNormalized(email);
        if (user == null || !user.active) {
            throw new NotAuthorizedException("User not found");
        }
        return UserProfileResponse.from(user);
    }

    private JsonNode verifyGoogleIdToken(String idToken) {
        try {
            String encoded = URLEncoder.encode(idToken.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + encoded))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new NotAuthorizedException("Google token verification failed");
            }
            return objectMapper.readTree(resp.body());
        } catch (NotAuthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Error verifying Google token", e);
        }
    }
}