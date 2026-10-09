package com.workshop.dto;

import com.workshop.entity.ContactStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New status for a contact submission")
public record ContactStatusRequest(
        @Schema(example = "RESOLVED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ContactStatus status) {
}
