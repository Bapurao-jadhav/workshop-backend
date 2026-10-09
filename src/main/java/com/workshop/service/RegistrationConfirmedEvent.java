package com.workshop.service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable snapshot published inside the registration transaction and consumed after commit,
 * so the email code never touches lazy entities and can never affect the registration itself.
 */
public record RegistrationConfirmedEvent(
        Long registrationId,
        String userName,
        String userEmail,
        String workshopTitle,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String venue,
        String meetingLink) {
}
