package com.workshop.controller;

import com.workshop.dto.PageResponse;
import com.workshop.dto.WorkshopResponse;
import com.workshop.service.WorkshopService;
import com.workshop.util.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workshops")
@Tag(name = "Workshops", description = "Public catalogue of published workshops")
public class PublicWorkshopController {

    static final Set<String> SORT_FIELDS = Set.of("workshopDate", "title", "createdAt", "fee", "capacity");

    private final WorkshopService workshopService;

    public PublicWorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @GetMapping
    @Operation(summary = "List published workshops (public)",
            description = "Only PUBLISHED workshops are returned. Supports free-text search, filters, "
                    + "pagination and sorting.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Page of workshops"))
    public PageResponse<WorkshopResponse> list(
            @Parameter(description = "Search in title, description and instructor name") @RequestParam(required = false) String q,
            @Parameter(description = "Exact category (case-insensitive)") @RequestParam(required = false) String category,
            @Parameter(description = "Target audience contains (case-insensitive)") @RequestParam(required = false) String audience,
            @Parameter(description = "Workshops on or after this date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Workshops on or before this date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as field,direction. Fields: workshopDate, title, createdAt, fee, capacity",
                    example = "workshopDate,asc") @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, SORT_FIELDS, Sort.by(Sort.Direction.ASC, "workshopDate"));
        return workshopService.searchPublished(q, category, audience, from, to, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a published workshop (public)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop details"),
            @ApiResponse(responseCode = "404", description = "Not found, or not published")
    })
    public WorkshopResponse get(@PathVariable Long id) {
        return workshopService.getPublished(id);
    }
}
