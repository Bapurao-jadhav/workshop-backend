package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login credentials")
public record LoginRequest(
        @Schema(example = "asha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email String email,

        @Schema(example = "Str0ngPass!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String password) {
}
