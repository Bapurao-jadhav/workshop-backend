package com.workshop.controller;

import com.workshop.dto.ContactResponse;
import com.workshop.dto.ContactStatusRequest;
import com.workshop.dto.PageResponse;
import com.workshop.entity.ContactStatus;
import com.workshop.service.ContactService;
import com.workshop.util.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/contacts")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Contacts", description = "Contact form submissions (ADMIN role required)")
public class AdminContactController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "status", "subject", "id");

    private final ContactService contactService;

    public AdminContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    @Operation(summary = "List contact submissions")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Page of submissions"))
    public PageResponse<ContactResponse> list(
            @Parameter(description = "Filter by status") @RequestParam(required = false) ContactStatus status,
            @Parameter(description = "Search name, email or subject") @RequestParam(required = false) String q,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as field,direction. Fields: createdAt, status, subject, id",
                    example = "createdAt,desc") @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contactService.list(status, q, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a contact submission")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ContactResponse get(@PathVariable Long id) {
        return contactService.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update the status of a contact submission")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ContactResponse updateStatus(@PathVariable Long id, @Valid @RequestBody ContactStatusRequest request) {
        return contactService.updateStatus(id, request.status());
    }
}
