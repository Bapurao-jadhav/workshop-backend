package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a new user account")
public record RegisterRequest(
        @Schema(example = "Asha Patil", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String name,

        @Schema(example = "asha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(example = "Str0ngPass!", description = "8-72 characters, at least one letter and one digit",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,

        @Schema(example = "+919876543210", description = "Optional, 7-15 digits with optional leading +")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits with an optional leading +")
        String phone) {
}
