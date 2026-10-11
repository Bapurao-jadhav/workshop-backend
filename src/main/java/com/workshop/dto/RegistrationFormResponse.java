package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Where a user goes to request a seat in a workshop")
public record RegistrationFormResponse(
        @Schema(example = "1") Long workshopId,
        @Schema(example = "Full-Stack Web Development Bootcamp") String workshopTitle,
        @Schema(description = "Open this Google Form in a new tab",
                example = "https://docs.google.com/forms/d/e/1FAIpQLSd.../viewform") String formUrl,
        @Schema(example = "Fill in the form using the same email as your account. Your request is sent to the "
                + "admin automatically; if it does not show up, come back and confirm your submission.")
        String instructions) {
}
