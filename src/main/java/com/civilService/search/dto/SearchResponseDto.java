package com.civilService.search.dto;

import java.util.List;

public record SearchResponseDto(
        List<SearchHitDto> results,
        int totalCount,
        long tookMs,
        int currentPage,
        int totalPages
) {
    public SearchResponseDto(List<SearchHitDto> results, int totalCount, long tookMs) {
        this(results, totalCount, tookMs, 1, 1);
    }
}
