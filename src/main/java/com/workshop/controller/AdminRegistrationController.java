package com.workshop.controller;

import com.workshop.dto.AdminRegistrationResponse;
import com.workshop.dto.PageResponse;
import com.workshop.entity.RegistrationStatus;
import com.workshop.service.RegistrationService;
import com.workshop.util.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Registrations", description = "View participants and registrations (ADMIN role required)")
public class AdminRegistrationController {

    private final RegistrationService registrationService;

    public AdminRegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/workshops/{workshopId}/participants")
    @Operation(summary = "List participants of a workshop")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of registrations for the workshop"),
            @ApiResponse(responseCode = "404", description = "Workshop not found")
    })
    public PageResponse<AdminRegistrationResponse> participants(
            @PathVariable Long workshopId,
            @Parameter(description = "Filter by registration status") @RequestParam(required = false) RegistrationStatus status,
            @Parameter(description = "Search participant name or email") @RequestParam(required = false) String q,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as field,direction. Fields: registeredAt, status, id",
                    example = "registeredAt,desc") @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, UserRegistrationController.SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "registeredAt"));
        return registrationService.listParticipants(workshopId, status, q, pageable);
    }

    @GetMapping("/registrations")
    @Operation(summary = "List all registrations with optional filters")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Page of registrations"))
    public PageResponse<AdminRegistrationResponse> all(
            @Parameter(description = "Only registrations of this workshop") @RequestParam(required = false) Long workshopId,
            @Parameter(description = "Filter by registration status") @RequestParam(required = false) RegistrationStatus status,
            @Parameter(description = "Search participant name or email") @RequestParam(required = false) String q,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as field,direction. Fields: registeredAt, status, id",
                    example = "registeredAt,desc") @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, UserRegistrationController.SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "registeredAt"));
        return registrationService.listAll(workshopId, status, q, pageable);
    }
}
