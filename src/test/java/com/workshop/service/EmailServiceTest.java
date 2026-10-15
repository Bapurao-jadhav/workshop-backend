package com.workshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.config.AppProperties;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailServiceTest {
    private AppProperties props;
    private RegistrationConfirmedEvent event;

    @BeforeEach
    void setUp() {
        props = new AppProperties(new AppProperties.Jwt("x", 1), new AppProperties.Cors(List.of()),
                new AppProperties.Admin("a", "b", "c"), new AppProperties.Mail("no-reply@workshops.test"), "UTC");
        event = new RegistrationConfirmedEvent(42L, "Asha Patil", "asha@example.com", "Java Bootcamp",
                LocalDate.of(2026, 12, 15), LocalTime.of(10, 0), LocalTime.of(16, 30), "Hall A", null);
    }

    @Test
    void confirmationHtmlContainsDetailsAndEscapesUserInput() {
        RegistrationConfirmedEvent evil = new RegistrationConfirmedEvent(42L, "<script>alert(1)</script>",
                "asha@example.com", "Java & Spring", LocalDate.of(2026, 12, 15), LocalTime.of(10, 0),
                LocalTime.of(16, 30), "Hall A", null);
        String html = EmailService.buildConfirmationHtml(evil);
        assertThat(html).contains("#42", "Java &amp; Spring", "Hall A", "Tuesday, 15 December 2026",
                "10:00 AM", "4:30 PM");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void resetEmailContainsButtonAndExpiryNotice() {
        String html = EmailService.buildPasswordResetHtml("https://example.com/reset-password?token=abc");
        assertThat(html).contains("Reset password", "https://example.com/reset-password?token=abc",
                "expires in 30 minutes");
    }
}
