package com.quantumstore.auth;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {}