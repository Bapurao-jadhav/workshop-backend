package com.workshop.dto;

import com.workshop.entity.Registration;
import com.workshop.entity.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A registration belonging to the authenticated user. "
        + "The online meeting link is only included while the registration is CONFIRMED.")
public record RegistrationResponse(
        @Schema(example = "15") Long id,
        @Schema(example = "CONFIRMED") RegistrationStatus status,
        Instant registeredAt,
        Instant cancelledAt,
        @Schema(description = "True when the user may still cancel this registration") boolean cancellable,
        WorkshopSummary workshop) {

    public static RegistrationResponse from(Registration r, boolean cancellable) {
        WorkshopSummary summary = WorkshopSummary.from(r.getWorkshop());
        if (r.getStatus() != RegistrationStatus.CONFIRMED) {
            summary = summary.withoutMeetingLink();
        }
        return new RegistrationResponse(r.getId(), r.getStatus(), r.getRegisteredAt(), r.getCancelledAt(),
                cancellable, summary);
    }
}
