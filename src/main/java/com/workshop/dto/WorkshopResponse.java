package com.workshop.dto;

import com.workshop.entity.Workshop;
import com.workshop.entity.WorkshopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "Workshop details. meetingLink is only returned to admins and to registered participants.")
public record WorkshopResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Full-Stack Web Development Bootcamp") String title,
        String description,
        @Schema(example = "Web Development") String category,
        @Schema(example = "Rahul Deshmukh") String instructorName,
        @Schema(example = "Final-year engineering students") String targetAudience,
        @Schema(example = "2026-12-15") LocalDate date,
        @Schema(type = "string", example = "10:00") LocalTime startTime,
        @Schema(type = "string", example = "16:00") LocalTime endTime,
        @Schema(example = "2026-12-10T18:00:00") LocalDateTime registrationDeadline,
        @Schema(example = "Auditorium B, Tech Park, Latur") String venue,
        @Schema(description = "True when the workshop has an online meeting link") boolean online,
        @Schema(description = "Visible to admins only on this endpoint") String meetingLink,
        @Schema(example = "60") int capacity,
        @Schema(example = "57") long availableSeats,
        @Schema(example = "3") long participantCount,
        @Schema(example = "499.00") BigDecimal fee,
        String imageUrl,
        @Schema(example = "PUBLISHED") WorkshopStatus status,
        @Schema(description = "True when status is PUBLISHED, deadline not passed and seats remain")
        boolean registrationOpen,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkshopResponse from(Workshop w, long confirmed, boolean registrationOpen,
                                        boolean includeMeetingLink) {
        return new WorkshopResponse(w.getId(), w.getTitle(), w.getDescription(), w.getCategory(),
                w.getInstructorName(), w.getTargetAudience(), w.getWorkshopDate(), w.getStartTime(),
                w.getEndTime(), w.getRegistrationDeadline(), w.getVenue(),
                w.getMeetingLink() != null, includeMeetingLink ? w.getMeetingLink() : null,
                w.getCapacity(), Math.max(0, w.getCapacity() - confirmed), confirmed, w.getFee(),
                w.getImageUrl(), w.getStatus(), registrationOpen, w.getCreatedAt(), w.getUpdatedAt());
    }
}
