package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "Payload for creating or fully replacing a workshop. "
        + "At least one of venue or meetingLink is required.")
public record WorkshopRequest(
        @Schema(example = "Full-Stack Web Development Bootcamp", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,

        @Schema(example = "A hands-on one-day workshop covering React, REST APIs and deployment.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 5000) String description,

        @Schema(example = "Web Development", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String category,

        @Schema(example = "Rahul Deshmukh", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 150) String instructorName,

        @Schema(example = "Final-year engineering students", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String targetAudience,

        @Schema(example = "2026-12-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate date,

        @Schema(type = "string", example = "10:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalTime startTime,

        @Schema(type = "string", example = "16:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalTime endTime,

        @Schema(example = "2026-12-10T18:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDateTime registrationDeadline,

        @Schema(example = "Auditorium B, Tech Park, Latur")
        @Size(max = 255) String venue,

        @Schema(example = "https://meet.example.com/abc-defg-hij")
        @Size(max = 500)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "must be a valid http(s) URL") String meetingLink,

        @Schema(example = "60", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(10000) Integer capacity,

        @Schema(example = "499.00", description = "Optional. Omit or use 0 for a free workshop.")
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal fee,

        @Schema(example = "https://cdn.example.com/workshops/fullstack.jpg")
        @Size(max = 500)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "must be a valid http(s) URL") String imageUrl) {
}
