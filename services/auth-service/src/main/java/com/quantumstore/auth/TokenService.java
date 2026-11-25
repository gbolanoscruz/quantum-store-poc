package com.quantumstore.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "quantum.auth.jwt-secret")
    String jwtSecret;

    @ConfigProperty(name = "quantum.auth.jwt-issuer")
    String issuer;

    @ConfigProperty(name = "quantum.auth.jwt-ttl-seconds")
    long ttlSeconds;

    @Inject
    ObjectMapper objectMapper;

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issueToken(UserAccount user) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + ttlSeconds;

            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String payloadJson = objectMapper.writeValueAsString(
                    new JwtClaims(
                            String.valueOf(user.id),
                            user.email,
                            user.fullName,
                            user.role,
                            issuer,
                            now,
                            exp
                    )
            );

            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = signHmacSha256(signingInput, jwtSecret);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Could not issue JWT token", e);
        }
    }

    public JsonNode parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            String expectedSig = signHmacSha256(signingInput, jwtSecret);
            if (!MessageDigest.isEqual(
                    expectedSig.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(decodedPayload);
            long exp = claims.path("exp").asLong(0L);
            if (exp <= Instant.now().getEpochSecond()) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private String signHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(raw);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record JwtClaims(
            String sub,
            String email,
            String name,
            String role,
            String iss,
            long iat,
            long exp
    ) {}
}