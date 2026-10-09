package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT token and the authenticated user")
public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzM4NCJ9...") String token,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Token lifetime in seconds", example = "7200") long expiresInSeconds,
        UserResponse user) {
}
