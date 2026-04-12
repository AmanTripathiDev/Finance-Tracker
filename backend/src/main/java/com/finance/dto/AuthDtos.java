package com.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @Schema(example = "alex@example.com")
            @Email @NotBlank String email,
            @Schema(example = "P@ssw0rd123")
            @NotBlank @Size(min = 8, max = 100) String password,
            @Schema(example = "Alex Morgan")
            @NotBlank @Size(max = 100) String displayName
    ) {
    }

    public record LoginRequest(
            @Schema(example = "alex@example.com")
            @Email @NotBlank String email,
            @Schema(example = "P@ssw0rd123")
            @NotBlank String password
    ) {
    }

    public record RefreshTokenRequest(
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
            @NotBlank String refreshToken
    ) {
    }

    public record LogoutRequest(
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
            @NotBlank String refreshToken
    ) {
    }

    public record ForgotPasswordRequest(
            @Schema(example = "alex@example.com")
            @Email @NotBlank String email
    ) {
    }

    public record ResetPasswordRequest(
            @Schema(example = "b7f4f2f0-7f45-4a8c-a3a4-0f3d8a8ed8c1")
            @NotBlank String token,
            @Schema(example = "N3wP@ssword1")
            @NotBlank
            @Size(min = 8, max = 100)
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must contain at least one letter and one number"
            )
            String newPassword
    ) {
    }

    public record MessageResponse(
            @Schema(example = "Password reset successful")
            String message
    ) {
    }

    public record AuthResponse(
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
            String refreshToken,
            @Schema(example = "Bearer")
            String tokenType,
            @Schema(example = "3600")
            long expiresIn,
            UserResponse user
    ) {
    }

    public record UserResponse(
            @Schema(example = "4e6d9a8e-2a21-4e3a-9f73-3f8c4a1d7f77")
            java.util.UUID id,
            @Schema(example = "alex@example.com")
            String email,
            @Schema(example = "Alex Morgan")
            String displayName
    ) {
    }
}
