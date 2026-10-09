package com.workshop.dto;

import com.workshop.entity.Workshop;
import com.workshop.entity.WorkshopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Compact workshop information embedded in registration responses")
public record WorkshopSummary(
        @Schema(example = "1") Long id,
        @Schema(example = "Full-Stack Web Development Bootcamp") String title,
        String category,
        String instructorName,
        @Schema(example = "2026-12-15") LocalDate date,
        @Schema(type = "string", example = "10:00") LocalTime startTime,
        @Schema(type = "string", example = "16:00") LocalTime endTime,
        String venue,
        String meetingLink,
        WorkshopStatus status) {

    public WorkshopSummary withoutMeetingLink() {
        return new WorkshopSummary(id, title, category, instructorName, date, startTime, endTime, venue, null,
                status);
    }

    public static WorkshopSummary from(Workshop w) {
        return new WorkshopSummary(w.getId(), w.getTitle(), w.getCategory(), w.getInstructorName(),
                w.getWorkshopDate(), w.getStartTime(), w.getEndTime(), w.getVenue(), w.getMeetingLink(),
                w.getStatus());
    }
}
