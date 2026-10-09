package com.workshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workshop.config.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> provider;
    private AppProperties props;
    private RegistrationConfirmedEvent event;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        props = new AppProperties(new AppProperties.Jwt("x", 1), new AppProperties.Cors(List.of()),
                new AppProperties.Admin("a", "b", "c"), new AppProperties.Mail("no-reply@workshops.test"), "UTC");
        event = new RegistrationConfirmedEvent(42L, "Asha Patil", "asha@example.com", "Java Bootcamp",
                LocalDate.of(2026, 12, 15), LocalTime.of(10, 0), LocalTime.of(16, 30), "Hall A", null);
    }

    @Test
    void sendsEmailWhenSmtpIsConfigured() {
        EmailService service = new EmailService(provider, props, "smtp.example.com");

        assertThat(service.sendRegistrationConfirmation(event)).isTrue();
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void swallowsSmtpFailures() {
        doThrow(new MailSendException("SMTP down")).when(sender).send(any(MimeMessage.class));
        EmailService service = new EmailService(provider, props, "smtp.example.com");

        assertThat(service.sendRegistrationConfirmation(event)).isFalse();
    }

    @Test
    void skipsSilentlyWithoutSmtpHost() {
        EmailService service = new EmailService(provider, props, "");

        assertThat(service.sendRegistrationConfirmation(event)).isFalse();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void htmlContainsAllDetailsAndEscapesUserInput() {
        RegistrationConfirmedEvent evil = new RegistrationConfirmedEvent(42L, "<script>alert(1)</script>",
                "asha@example.com", "Java & Spring", LocalDate.of(2026, 12, 15), LocalTime.of(10, 0),
                LocalTime.of(16, 30), "Hall A", null);

        String html = EmailService.buildConfirmationHtml(evil);

        assertThat(html).contains("#42", "Java &amp; Spring", "Hall A", "Tuesday, 15 December 2026",
                "10:00 AM", "4:30 PM");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }
}
