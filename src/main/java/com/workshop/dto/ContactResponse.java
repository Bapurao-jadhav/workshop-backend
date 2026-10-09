package com.workshop.dto;

import com.workshop.entity.ContactStatus;
import com.workshop.entity.ContactSubmission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A saved contact form submission")
public record ContactResponse(
        @Schema(example = "3") Long id,
        String name,
        String email,
        String phone,
        String subject,
        String message,
        @Schema(example = "NEW") ContactStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ContactResponse from(ContactSubmission c) {
        return new ContactResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getSubject(),
                c.getMessage(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
