package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Reset a password using a one-time reset token")
public record ResetPasswordRequest(
        @Schema(description = "One-time token from the reset email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String token,

        @Schema(example = "Str0ngPass!", description = "8-72 characters, at least one letter and one digit",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "must contain at least one letter and one digit") String newPassword) {
}
