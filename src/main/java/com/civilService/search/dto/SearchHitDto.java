package com.civilService.search.dto;

import java.time.LocalDateTime;

public record SearchHitDto(
        Long id,
        String fullNameHtml,
        String titleHtml,
        String agencyHtml,
        String examNo,
        String listNo,
        String status,
        int score,
        boolean isCertified,
        String certifiedAgency,
        LocalDateTime certifiedDate,
        String formattedCertifiedDate,
        boolean isReached,
        String latestReachAgency,
        LocalDateTime latestReachDate,
        String formattedLatestReachDate
) {}
