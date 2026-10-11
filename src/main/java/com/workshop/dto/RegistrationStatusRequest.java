package com.workshop.dto;

import com.workshop.entity.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin decision for a pending workshop registration")
public record RegistrationStatusRequest(
        @NotNull @Schema(description = "Only CONFIRMED or REJECTED is allowed", example = "CONFIRMED")
        RegistrationStatus status) {
}
