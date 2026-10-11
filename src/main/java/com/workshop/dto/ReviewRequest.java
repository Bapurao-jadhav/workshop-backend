package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional note attached to an approval or rejection")
public record ReviewRequest(
        @Schema(example = "Verified in the Google Form responses") @Size(max = 500) String note) {
}
