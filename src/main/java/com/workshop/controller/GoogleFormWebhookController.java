package com.workshop.controller;

import com.workshop.dto.GoogleFormSubmissionRequest;
import com.workshop.dto.RegistrationResponse;
import com.workshop.entity.SubmissionSource;
import com.workshop.exception.UnauthorizedException;
import com.workshop.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by a small Google Apps Script attached to each workshop's Google Form ("on form submit" trigger).
 * Not protected by a JWT; it is protected by the shared secret in GOOGLE_FORM_WEBHOOK_SECRET.
 */
@RestController
@RequestMapping("/api/integrations/google-forms")
@Tag(name = "Google Form integration", description = "Webhook called by the Google Form's Apps Script")
public class GoogleFormWebhookController {

    private final RegistrationService registrationService;
    private final byte[] secret;

    public GoogleFormWebhookController(RegistrationService registrationService,
                                       @Value("${app.google-form.webhook-secret:}") String secret) {
        this.registrationService = registrationService;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "A Google Form was submitted (called by the form's Apps Script)",
            description = "Creates a PENDING registration request for the user with this email, to be approved by "
                    + "an admin. Requires the X-Webhook-Secret header to equal GOOGLE_FORM_WEBHOOK_SECRET. "
                    + "Disabled (401) when that variable is not set.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request created with status PENDING"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong secret, or webhook disabled"),
            @ApiResponse(responseCode = "404", description = "Unknown workshop, or no account with this email"),
            @ApiResponse(responseCode = "409", description = "Already pending/confirmed, or workshop is full"),
            @ApiResponse(responseCode = "422", description = "Workshop cancelled, unpublished or deadline passed")
    })
    public RegistrationResponse submission(
            @Parameter(description = "Shared secret configured on the server", required = true)
            @RequestHeader(value = "X-Webhook-Secret", required = false) String provided,
            @Valid @RequestBody GoogleFormSubmissionRequest request) {
        if (secret.length == 0) {
            throw new UnauthorizedException("The Google Form webhook is not enabled on this server");
        }
        byte[] given = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(secret, given)) {
            throw new UnauthorizedException("Invalid webhook secret");
        }
        return registrationService.requestRegistrationByEmail(request.workshopId(), request.email(),
                SubmissionSource.FORM_WEBHOOK);
    }
}
