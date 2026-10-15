package com.workshop.service;

import com.workshop.config.AppProperties;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends transactional emails through Brevo HTTPS API only. No SMTP fallback is used.
 * Email failures are logged and do not break registration processing.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final AppProperties props;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    @Value("${app.mail.sender-name:arQnare}")
    private String brevoSenderName;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EmailService(AppProperties props) {
        this.props = props;
    }

    /** Brevo requires a verified sender address; never fall back to old SMTP credentials. */
    private String fromAddress() {
        return props.mail() != null && StringUtils.hasText(props.mail().from())
                ? props.mail().from().trim()
                : null;
    }

    /** Sends an email using Brevo's HTTPS API (Render blocks outbound SMTP ports on free services). */
    public boolean sendRegistrationConfirmation(RegistrationConfirmedEvent event) {
        return sendHtmlEmail(event.userEmail(),
                "Registration confirmed: " + event.workshopTitle(),
                buildConfirmationHtml(event),
                "confirmation email for registration " + event.registrationId());
    }

    /** Sends a one-time password reset link. Failures never reveal account existence through the API response. */
    public boolean sendPasswordReset(String recipientEmail, String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            log.warn("Password reset email skipped because token is blank");
            return false;
        }
        String baseUrl = StringUtils.hasText(frontendUrl) ? frontendUrl.trim() : "http://localhost:5173";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String resetUrl = baseUrl + "/reset-password?token="
                + java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
        return sendHtmlEmail(recipientEmail, "Reset password", buildPasswordResetHtml(resetUrl),
                "password reset email");
    }

    /** Sends a rejection notice after an admin rejects a pending registration. */
    public boolean sendRegistrationRejection(RegistrationRejectedEvent event) {
        return sendHtmlEmail(event.userEmail(),
                "Registration update: " + event.workshopTitle(),
                buildRejectionHtml(event),
                "rejection email for registration " + event.registrationId());
    }

    private boolean sendHtmlEmail(String recipientEmail, String subject, String html, String description) {
        String from = fromAddress();
        if (!StringUtils.hasText(brevoApiKey) || from == null || !StringUtils.hasText(recipientEmail)) {
            log.warn("Brevo email not configured (BREVO_API_KEY and MAIL_FROM are required); skipping {}", description);
            return false;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("name", StringUtils.hasText(brevoSenderName) ? brevoSenderName : "arQnare",
                            "email", from),
                    "to", List.of(Map.of("email", recipientEmail)),
                    "subject", subject,
                    "htmlContent", html
            );

            RestClient.create("https://api.brevo.com")
                    .post()
                    .uri("/v3/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", brevoApiKey.trim())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Brevo accepted {}", description);
            return true;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            // Brevo's response body commonly explains invalid keys, unverified senders, or invalid recipients.
            // Do not log request headers or API keys.
            log.warn("Brevo rejected {} (HTTP {}): {}", description, ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.warn("Could not send {} through Brevo ({}): {}", description,
                    ex.getClass().getSimpleName(), ex.getMessage());
            return false;
        }
    }

    static String buildPasswordResetHtml(String resetUrl) {
        String safeUrl = HtmlUtils.htmlEscape(resetUrl);
        return """
                <!DOCTYPE html>
                <html><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2933;line-height:1.6;">
                  <h2>Reset your password</h2>
                  <p>We received a request to reset the password for your account.</p>
                  <p><a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:6px;font-weight:bold;">Reset password</a></p>
                  <p>This link expires in 30 minutes and can be used only once.</p>
                  <p>If you did not request this, you can safely ignore this email.</p>
                  <hr/><small>This is an automated message, please do not reply.</small>
                </body></html>
                """.formatted(safeUrl);
    }

    /** Sends a rejection notice after an admin rejects a pending registration. */
  
    static String buildRejectionHtml(RegistrationRejectedEvent e) {
        return """
                <!DOCTYPE html>
                <html><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2933;line-height:1.6;">
                  <h2>Registration Update</h2>
                  <p>Hi %s,</p>
                  <p>Thank you for your interest in <strong>%s</strong>.</p>
                  <p>Unfortunately, your registration (#%d) was not approved.</p>
                  <p>If you believe this is an error, please contact the workshop organizer.</p>
                  <p>Regards,<br/>Workshop Team</p>
                  <hr/><small>This is an automated message, please do not reply.</small>
                </body></html>
                """.formatted(HtmlUtils.htmlEscape(e.userName()), HtmlUtils.htmlEscape(e.workshopTitle()),
                e.registrationId());
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
