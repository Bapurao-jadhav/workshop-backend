package com.workshop.controller;

import com.workshop.dto.PageResponse;
import com.workshop.dto.RegistrationResponse;
import com.workshop.entity.RegistrationStatus;
import com.workshop.security.AppUserPrincipal;
import com.workshop.service.RegistrationService;
import com.workshop.util.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Registrations", description = "Register for workshops and manage your own registrations")
public class UserRegistrationController {

    static final Set<String> SORT_FIELDS = Set.of("registeredAt", "status", "id");

    private final RegistrationService registrationService;

    public UserRegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/api/workshops/{workshopId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register the current user for a workshop",
            description = "Fails if the workshop is full, cancelled, unpublished, past its registration deadline, "
                    + "or the user is already registered. A confirmation email is sent after success.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered"),
            @ApiResponse(responseCode = "404", description = "Workshop not found"),
            @ApiResponse(responseCode = "409", description = "Already registered, or workshop is full"),
            @ApiResponse(responseCode = "422", description = "Workshop cancelled, unpublished or deadline passed")
    })
    public RegistrationResponse register(@PathVariable Long workshopId,
                                         @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me) {
        return registrationService.register(me.id(), workshopId);
    }

    @GetMapping("/api/registrations/me")
    @Operation(summary = "List my registrations")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Page of my registrations"))
    public PageResponse<RegistrationResponse> mine(
            @Parameter(description = "Filter by registration status") @RequestParam(required = false) RegistrationStatus status,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as field,direction. Fields: registeredAt, status, id",
                    example = "registeredAt,desc") @RequestParam(required = false) String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me) {
        var pageable = PageableFactory.of(page, size, sort, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "registeredAt"));
        return registrationService.listMine(me.id(), status, pageable);
    }

    @GetMapping("/api/registrations/me/{registrationId}")
    @Operation(summary = "Get one of my registrations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration details"),
            @ApiResponse(responseCode = "404", description = "Not found (or belongs to another user)")
    })
    public RegistrationResponse getMine(@PathVariable Long registrationId,
                                        @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me) {
        return registrationService.getMine(me.id(), registrationId);
    }

    @DeleteMapping("/api/registrations/me/{registrationId}")
    @Operation(summary = "Cancel one of my registrations",
            description = "Allowed only while the registration is CONFIRMED and the workshop has not started. "
                    + "The registration is kept with status CANCELLED and the seat is freed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration cancelled"),
            @ApiResponse(responseCode = "404", description = "Not found (or belongs to another user)"),
            @ApiResponse(responseCode = "422", description = "Already cancelled or workshop already started")
    })
    public RegistrationResponse cancel(@PathVariable Long registrationId,
                                       @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me) {
        return registrationService.cancelMine(me.id(), registrationId);
    }
}
