package com.workshop.controller;

import com.workshop.dto.PageResponse;
import com.workshop.dto.WorkshopRequest;
import com.workshop.dto.WorkshopResponse;
import com.workshop.dto.WorkshopStatusRequest;
import com.workshop.entity.WorkshopStatus;
import com.workshop.service.WorkshopService;
import com.workshop.util.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/workshops")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Workshops", description = "Workshop management (ADMIN role required)")
public class AdminWorkshopController {

    private final WorkshopService workshopService;

    public AdminWorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @GetMapping
    @Operation(summary = "List all workshops with participant counts",
            description = "Includes DRAFT, UNPUBLISHED and CANCELLED workshops.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Page of workshops"))
    public PageResponse<WorkshopResponse> list(
            @Parameter(description = "Search in title, description and instructor name") @RequestParam(required = false) String q,
            @Parameter(description = "Exact category (case-insensitive)") @RequestParam(required = false) String category,
            @Parameter(description = "Filter by status") @RequestParam(required = false) WorkshopStatus status,
            @Parameter(description = "Workshops on or after this date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Workshops on or before this date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as field,direction. Fields: workshopDate, title, createdAt, fee, capacity",
                    example = "createdAt,desc") @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, PublicWorkshopController.SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return workshopService.searchAll(q, category, status, from, to, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any workshop by id, including its meeting link")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop details"),
            @ApiResponse(responseCode = "404", description = "Workshop not found")
    })
    public WorkshopResponse get(@PathVariable Long id) {
        return workshopService.getForAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workshop",
            description = "The workshop starts as DRAFT. Publish it with PATCH /{id}/status.")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Workshop created"))
    public WorkshopResponse create(@Valid @RequestBody WorkshopRequest request) {
        return workshopService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the editable fields of a workshop",
            description = "Capacity cannot be lowered below the number of confirmed participants. "
                    + "Cancelled workshops cannot be edited.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop updated"),
            @ApiResponse(responseCode = "404", description = "Workshop not found"),
            @ApiResponse(responseCode = "422", description = "Business rule violated")
    })
    public WorkshopResponse update(@PathVariable Long id, @Valid @RequestBody WorkshopRequest request) {
        return workshopService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publish, unpublish or cancel a workshop",
            description = "PUBLISHED from DRAFT/UNPUBLISHED; UNPUBLISHED from PUBLISHED; CANCELLED from any "
                    + "non-cancelled status (all confirmed registrations are cancelled too).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "404", description = "Workshop not found"),
            @ApiResponse(responseCode = "422", description = "Transition not allowed")
    })
    public WorkshopResponse changeStatus(@PathVariable Long id, @Valid @RequestBody WorkshopStatusRequest request) {
        return workshopService.changeStatus(id, request.status());
    }
}
