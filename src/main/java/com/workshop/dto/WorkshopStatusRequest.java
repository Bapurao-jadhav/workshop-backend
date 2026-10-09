package com.workshop.dto;

import com.workshop.entity.WorkshopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Target status. Allowed: PUBLISHED (from DRAFT/UNPUBLISHED), UNPUBLISHED (from PUBLISHED), "
        + "CANCELLED (from any non-cancelled status; cancels all confirmed registrations).")
public record WorkshopStatusRequest(
        @Schema(example = "PUBLISHED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull WorkshopStatus status) {
}
