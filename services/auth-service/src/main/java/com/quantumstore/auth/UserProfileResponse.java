package com.quantumstore.auth;

public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        String authProvider,
        String role
) {
    static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(
                user.id,
                user.email,
                user.fullName,
                user.authProvider.name(),
                user.role
        );
    }
}