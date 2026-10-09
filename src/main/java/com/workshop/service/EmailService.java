package com.workshop.service;

import com.workshop.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends transactional emails. Never throws: a failed email must not break or duplicate a registration.
 * When no SMTP host is configured the email is only logged, so the app runs locally without credentials.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties props;
    private final String mailHost;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties props,
                        @Value("${spring.mail.host:}") String mailHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.props = props;
        this.mailHost = mailHost;
    }

    /** @return true if the email was handed to the SMTP server, false if skipped or failed */
    public boolean sendRegistrationConfirmation(RegistrationConfirmedEvent event) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || !StringUtils.hasText(mailHost)) {
            log.info("SMTP is not configured; skipping confirmation email for registration {}",
                    event.registrationId());
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(event.userEmail());
            helper.setSubject("Registration confirmed: " + event.workshopTitle());
            helper.setText(buildConfirmationHtml(event), true);
            sender.send(message);
            log.info("Confirmation email sent for registration {}", event.registrationId());
            return true;
        } catch (Exception ex) {
            log.warn("Could not send confirmation email for registration {}: {}", event.registrationId(),
                    ex.getMessage());
            return false;
        }
    }

    static String buildConfirmationHtml(RegistrationConfirmedEvent e) {
        String where;
        if (StringUtils.hasText(e.venue())) {
            where = HtmlUtils.htmlEscape(e.venue());
            if (StringUtils.hasText(e.meetingLink())) {
                where += " (also online)";
            }
        } else {
            where = "Online";
        }
        String link = StringUtils.hasText(e.meetingLink())
                ? row("Join link", "<a href=\"" + HtmlUtils.htmlEscape(e.meetingLink()) + "\">"
                + HtmlUtils.htmlEscape(e.meetingLink()) + "</a>")
                : "";
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#1f2933;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:24px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;">
                        <tr><td style="background:#2563eb;color:#ffffff;padding:20px 28px;font-size:20px;font-weight:bold;">
                          Registration Confirmed</td></tr>
                        <tr><td style="padding:28px;">
                          <p style="margin:0 0 16px;">Hi %s,</p>
                          <p style="margin:0 0 20px;">You are registered for <strong>%s</strong>. Here are your details:</p>
                          <table role="presentation" width="100%%" cellpadding="8" cellspacing="0"
                                 style="border:1px solid #e5e7eb;border-radius:6px;">
                            %s%s%s%s%s
                          </table>
                          <p style="margin:24px 0 0;">Please keep your registration ID handy. We look forward to seeing you!</p>
                        </td></tr>
                        <tr><td style="background:#f9fafb;padding:16px 28px;font-size:12px;color:#6b7280;">
                          This is an automated message, please do not reply.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(e.userName()),
                HtmlUtils.htmlEscape(e.workshopTitle()),
                row("Registration ID", "#" + e.registrationId()),
                row("Workshop", HtmlUtils.htmlEscape(e.workshopTitle())),
                row("Date", e.date().format(DATE_FORMAT)),
                row("Time", e.startTime().format(TIME_FORMAT) + " - " + e.endTime().format(TIME_FORMAT)),
                row("Venue", where) + link);
    }

    private static String row(String label, String valueHtml) {
        return "<tr><td style=\"color:#6b7280;width:140px;\">" + label
                + "</td><td style=\"font-weight:bold;\">" + valueHtml + "</td></tr>";
    }
}
