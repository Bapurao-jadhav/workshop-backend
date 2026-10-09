package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Contact Us form")
public record ContactRequest(
        @Schema(example = "Sneha Kulkarni", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String name,

        @Schema(example = "sneha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(example = "+919812345678")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits with an optional leading +")
        String phone,

        @Schema(example = "Group booking for our college", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String subject,

        @Schema(example = "We would like to organise a workshop for 120 students. Please share the details.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 10, max = 5000) String message) {
}
