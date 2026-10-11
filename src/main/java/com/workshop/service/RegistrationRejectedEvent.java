package com.workshop.service;

import java.time.LocalDate;
import java.time.LocalTime;

/** Snapshot used to send rejection email after the transaction commits. */
public record RegistrationRejectedEvent(
        Long registrationId,
        String userName,
        String userEmail,
        String workshopTitle,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String venue) {
}
