package com.agent.agentplatform.dto;
import org.springframework.data.domain.*;
import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {
    public static Pageable request(int page, int size) {
        if (page < 0 || size < 1 || size > 100) { throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 100"); }
        return PageRequest.of(page, size);
    }
    public static String query(String query) {
        if (query.length() > 100) { throw new IllegalArgumentException("Search must not exceed 100 characters"); }
        return query.strip().toLowerCase(java.util.Locale.ROOT);
    }
    public static <E,T> PageResult<T> from(Page<E> result, Function<E,T> mapper) {
        return new PageResult<>(result.getContent().stream().map(mapper).toList(), result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
    }
}
