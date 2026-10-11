package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Sent by the Google Apps Script attached to a workshop's form when someone submits it")
public record GoogleFormSubmissionRequest(
        @Schema(description = "Id of the workshop this form belongs to", example = "12",
                requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long workshopId,
        @Schema(description = "Email of the person who submitted the form. Must match their account email",
                example = "asha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email String email) {
}
