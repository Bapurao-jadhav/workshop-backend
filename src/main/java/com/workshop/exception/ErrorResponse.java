package com.workshop.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Consistent JSON error body returned by every failing endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public record ErrorResponse(
        @Schema(example = "2026-10-09T08:30:00Z") Instant timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "This workshop is full") String message,
        @Schema(example = "/api/workshops/1/registrations") String path,
        @Schema(description = "Present only for validation errors") List<FieldViolation> fieldErrors) {

    public record FieldViolation(
            @Schema(example = "email") String field,
            @Schema(example = "must be a well-formed email address") String message) {
    }
}
