package com.workshop.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the confirmation email only after the registration transaction has committed, off the request thread. */
@Component
public class RegistrationEmailListener {

    private final EmailService emailService;

    public RegistrationEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationConfirmed(RegistrationConfirmedEvent event) {
        emailService.sendRegistrationConfirmation(event);
    }
}
