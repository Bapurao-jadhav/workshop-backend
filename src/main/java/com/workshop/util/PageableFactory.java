package com.workshop.util;

import com.workshop.exception.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds safe {@link Pageable}s: clamps page size and only allows whitelisted sort fields. */
public final class PageableFactory {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private PageableFactory() {
    }

    /**
     * @param sort optional value such as {@code date,asc} or {@code title,desc}
     */
    public static Pageable of(int page, int size, String sort, Set<String> allowedFields, Sort defaultSort) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Sort resolved = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            if (!allowedFields.contains(field)) {
                throw new BadRequestException("Unsupported sort field '" + field + "'. Allowed: "
                        + String.join(", ", allowedFields.stream().sorted().toList()));
            }
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            resolved = Sort.by(direction, field);
        }
        return PageRequest.of(safePage, safeSize, resolved.and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
