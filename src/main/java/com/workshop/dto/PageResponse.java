package com.workshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

@Schema(description = "Paginated result")
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(example = "10") int size,
        @Schema(example = "42") long totalElements,
        @Schema(example = "5") int totalPages,
        boolean first,
        boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    public static <T> PageResponse<T> ofContent(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
