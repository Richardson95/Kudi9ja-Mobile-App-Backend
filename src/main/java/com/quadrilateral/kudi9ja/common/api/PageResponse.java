package com.quadrilateral.kudi9ja.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A page of results, flattened to what a mobile client actually needs: the
 * rows, where it is, and whether to keep scrolling.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    public static <T> PageResponse<T> of(List<T> all) {
        return new PageResponse<>(all, 0, all.size(), all.size(), 1, false);
    }
}
