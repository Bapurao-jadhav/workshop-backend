package com.workshop.dto;

import com.workshop.entity.Registration;
import com.workshop.entity.RegistrationStatus;
import com.workshop.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Registration as seen by an admin, including participant contact details")
public record AdminRegistrationResponse(
        @Schema(example = "15") Long id,
        @Schema(example = "CONFIRMED") RegistrationStatus status,
        Instant registeredAt,
        Instant cancelledAt,
        Participant participant,
        WorkshopSummary workshop) {

    @Schema(description = "Participant details")
    public record Participant(
            @Schema(example = "7") Long id,
            @Schema(example = "Asha Patil") String name,
            @Schema(example = "asha@example.com") String email,
            @Schema(example = "+919876543210") String phone) {

        static Participant from(User u) {
            return new Participant(u.getId(), u.getName(), u.getEmail(), u.getPhone());
        }
    }

    public static AdminRegistrationResponse from(Registration r) {
        return new AdminRegistrationResponse(r.getId(), r.getStatus(), r.getRegisteredAt(), r.getCancelledAt(),
                Participant.from(r.getUser()), WorkshopSummary.from(r.getWorkshop()));
    }
}
