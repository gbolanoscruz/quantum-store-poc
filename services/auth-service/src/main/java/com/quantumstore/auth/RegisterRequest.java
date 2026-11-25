package com.quantumstore.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 128) String password
) {}