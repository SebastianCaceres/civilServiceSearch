package com.civilService.search.service;

import com.civilService.search.dto.SearchHitDto;
import com.civilService.search.dto.SearchResponseDto;
import com.civilService.search.dto.CertificationEstimationDto;
import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.entity.CivilServiceRecord;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service providing full-text search capability over civil service list records
 * using the Lucene indexes managed by Hibernate Search, and resolving certifications.
 */
@Service
@Slf4j
public class SearchService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?:\u201C([^\u201C\u201D]+)\u201D|([^\\s]+))");

    private final SearchMapping searchMapping;
    private final CivilServiceSyncService syncService;
    private final CivilServiceListSyncService listSyncService;

    public SearchService(SearchMapping searchMapping, CivilServiceSyncService syncService, CivilServiceListSyncService listSyncService) {
        this.searchMapping = searchMapping;
        this.syncService = syncService;
        this.listSyncService = listSyncService;
    }

    /**
     * Search for candidate entries matching the query string in Lucene.
     */
    public SearchResponseDto searchEntries(String query) {
        return searchEntries(query, 1, Integer.MAX_VALUE);
    }

    public SearchResponseDto searchEntries(String query, int page, int pageSize) {
        if (query == null || query.isBlank()) {
            return new SearchResponseDto(Collections.emptyList(), 0, 0, 1, 1);
        }

        long start = System.currentTimeMillis();
        ParsedQuery parsed = parseQuery(query);

        // Fetch candidate list records matching query using Lucene
        List<CivilServiceListRecord> luceneHits = new ArrayList<>();
        try (SearchSession session = searchMapping.createSession()) {
            List<CivilServiceListRecord> searchResult = session.search(CivilServiceListRecord.class)
                    .select(CivilServiceListRecord.class)
                    .where(f -> f.bool(b -> {
                        for (String term : parsed.positiveTerms()) {
                            b.must(f.or(
                                f.match().field("firstName").matching(term),
                                f.match().field("lastName").matching(term),
                                f.match().field("listTitleDesc").matching(term),
                                f.match().field("listAgencyDesc").matching(term),
                                f.match().field("examNo").matching(term),
                                f.match().field("status").matching(term)
                            ));
                        }
                        for (String term : parsed.negativeTerms()) {
                            b.mustNot(f.or(
                                f.match().field("firstName").matching(term),
                                f.match().field("lastName").matching(term),
                                f.match().field("listTitleDesc").matching(term),
                                f.match().field("listAgencyDesc").matching(term),
                                f.match().field("examNo").matching(term),
                                f.match().field("status").matching(term)
                            ));
                        }
                        if (parsed.positiveTerms().isEmpty() && !parsed.negativeTerms().isEmpty()) {
                            b.must(f.matchAll());
                        }
                    }))
                    .fetchAllHits();
            luceneHits.addAll(searchResult);
        } catch (Exception e) {
            log.error("Error running Lucene search query", e);
        }

        List<ScoredHit> scored = new ArrayList<>();
        for (CivilServiceListRecord record : luceneHits) {
            int score = scoreRecord(record, parsed);
            if (score > 0) {
                scored.add(new ScoredHit(record, score));
            }
        }

        // Sort by score descending, then by list_no ascending
        scored.sort((a, b) -> {
            int cmp = Integer.compare(b.score(), a.score());
            if (cmp != 0) return cmp;
            if (a.entry().getListNo() != null && b.entry().getListNo() != null) {
                return a.entry().getListNo().compareTo(b.entry().getListNo());
            }
            return 0;
        });

        int totalCount = scored.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages < 1) {
            totalPages = 1;
        }

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);

        List<SearchHitDto> results = new ArrayList<>();
        if (fromIndex < totalCount && fromIndex >= 0) {
            List<ScoredHit> sublist = scored.subList(fromIndex, toIndex);
            for (ScoredHit hit : sublist) {
                CivilServiceListRecord record = hit.entry();
                results.add(new SearchHitDto(
                        record.getId(),
                        highlight(buildFullName(record), parsed.positiveTerms()),
                        highlight(record.getListTitleDesc(), parsed.positiveTerms()),
                        highlight(record.getListAgencyDesc(), parsed.positiveTerms()),
                        record.getExamNo(),
                        record.getListNo() != null ? record.getListNo().stripTrailingZeros().toPlainString() : "",
                        record.getStatus(),
                        hit.score(),
                        false, // isCertified
                        null,  // certifiedAgency
                        null,  // certifiedDate
                        null,  // formattedCertifiedDate
                        false, // isReached
                        null,  // latestReachAgency
                        null,  // latestReachDate
                        null   // formattedLatestReachDate
                ));
            }
        }

        long took = System.currentTimeMillis() - start;
        return new SearchResponseDto(results, totalCount, took, page, totalPages);
    }

    public SearchResponseDto searchEntries(String fullName, String examOrTitle, int page, int pageSize) {
        if ((fullName == null || fullName.isBlank()) && (examOrTitle == null || examOrTitle.isBlank())) {
            return new SearchResponseDto(Collections.emptyList(), 0, 0, 1, 1);
        }

        long start = System.currentTimeMillis();

        // Match candidate list records using Lucene
        List<CivilServiceListRecord> luceneHits = new ArrayList<>();
        try (SearchSession session = searchMapping.createSession()) {
            List<CivilServiceListRecord> searchResult = session.search(CivilServiceListRecord.class)
                    .select(CivilServiceListRecord.class)
                    .where(f -> f.bool(b -> {
                        // Match fullName terms against firstName and lastName
                        if (fullName != null && !fullName.isBlank()) {
                            String[] nameTerms = fullName.trim().toLowerCase().split("\\s+");
                            for (String term : nameTerms) {
                                b.must(f.or(
                                    f.match().field("firstName").matching(term),
                                    f.match().field("lastName").matching(term)
                                ));
                            }
                        }
                        
                        // Match examOrTitle against examNo or listTitleDesc
                        if (examOrTitle != null && !examOrTitle.isBlank()) {
                            b.must(f.or(
                                f.match().field("examNo").matching(examOrTitle.trim()),
                                f.match().field("listTitleDesc").matching(examOrTitle.trim())
                            ));
                        }
                    }))
                    .fetchAllHits();
            luceneHits.addAll(searchResult);
        } catch (Exception e) {
            log.error("Error running Lucene structured search query", e);
        }

        // Build positive terms parsed query for scoring & highlighting
        StringBuilder sb = new StringBuilder();
        if (fullName != null && !fullName.isBlank()) sb.append(fullName).append(" ");
        if (examOrTitle != null && !examOrTitle.isBlank()) sb.append(examOrTitle).append(" ");
        ParsedQuery parsed = parseQuery(sb.toString());

        List<ScoredHit> scored = new ArrayList<>();
        for (CivilServiceListRecord record : luceneHits) {
            int score = scoreRecord(record, parsed);
            if (score > 0) {
                scored.add(new ScoredHit(record, score));
            }
        }

        // Sort by score descending, then by list_no ascending
        scored.sort((a, b) -> {
            int cmp = Integer.compare(b.score(), a.score());
            if (cmp != 0) return cmp;
            if (a.entry().getListNo() != null && b.entry().getListNo() != null) {
                return a.entry().getListNo().compareTo(b.entry().getListNo());
            }
            return 0;
        });

        int totalCount = scored.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages < 1) {
            totalPages = 1;
        }

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);

        List<SearchHitDto> results = new ArrayList<>();
        if (fromIndex < totalCount && fromIndex >= 0) {
            List<ScoredHit> sublist = scored.subList(fromIndex, toIndex);
            for (ScoredHit hit : sublist) {
                CivilServiceListRecord record = hit.entry();
                results.add(new SearchHitDto(
                        record.getId(),
                        highlight(buildFullName(record), parsed.positiveTerms()),
                        highlight(record.getListTitleDesc(), parsed.positiveTerms()),
                        highlight(record.getListAgencyDesc(), parsed.positiveTerms()),
                        record.getExamNo(),
                        record.getListNo() != null ? record.getListNo().stripTrailingZeros().toPlainString() : "",
                        record.getStatus(),
                        hit.score(),
                        false, // isCertified
                        null,  // certifiedAgency
                        null,  // certifiedDate
                        null,  // formattedCertifiedDate
                        false, // isReached
                        null,  // latestReachAgency
                        null,  // latestReachDate
                        null   // formattedLatestReachDate
                ));
            }
        }

        long took = System.currentTimeMillis() - start;
        return new SearchResponseDto(results, totalCount, took, page, totalPages);
    }

    public CivilServiceListRecord getRecordById(Long id) {
        try (SearchSession session = searchMapping.createSession()) {
            return session.search(CivilServiceListRecord.class)
                    .select(CivilServiceListRecord.class)
                    .where(f -> f.id().matching(id))
                    .fetchSingleHit()
                    .orElse(null);
        }
    }

    private int scoreRecord(CivilServiceListRecord entry, ParsedQuery parsed) {
        String fullName = buildFullName(entry).toLowerCase();
        String title = safeLower(entry.getListTitleDesc());
        String agency = safeLower(entry.getListAgencyDesc());
        String status = safeLower(entry.getStatus());
        String examNo = safeLower(entry.getExamNo());
        String fullText = (fullName + " " + title + " " + agency + " " + status + " " + examNo).trim();

        for (String negative : parsed.negativeTerms()) {
            if (fullText.contains(negative)) {
                return -1;
            }
        }

        int score = 0;
        for (String positive : parsed.positiveTerms()) {
            if (!fullText.contains(positive)) {
                return -1;
            }

            score += 12;
            if (title.contains(positive)) {
                score += 15;
            }
            if (fullName.startsWith(positive)) {
                score += 20;
            }
            if (fullName.equals(positive)) {
                score += 120;
            }
        }

        return score;
    }

    private ParsedQuery parseQuery(String query) {
        List<String> positive = new ArrayList<>();
        List<String> negative = new ArrayList<>();

        Matcher matcher = TOKEN_PATTERN.matcher(query);
        while (matcher.find()) {
            String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (token == null || token.isBlank()) {
                continue;
            }

            String normalized = token.toLowerCase().trim();
            if (normalized.startsWith("-") && normalized.length() > 1) {
                negative.add(normalized.substring(1));
            } else {
                positive.add(normalized);
            }
        }

        return new ParsedQuery(positive, negative);
    }

    private String buildFullName(CivilServiceListRecord entry) {
        String first = defaultString(entry.getFirstName());
        String mi = defaultString(entry.getMi());
        String last = defaultString(entry.getLastName());
        String combined = (first + " " + mi + " " + last).replaceAll("\\s+", " ").trim();
        return combined;
    }

    private String highlight(String input, List<String> positiveTerms) {
        String raw = defaultString(input);
        if (raw.isBlank() || positiveTerms.isEmpty()) {
            return HtmlUtils.htmlEscape(raw);
        }

        String lower = raw.toLowerCase();
        List<Range> ranges = new ArrayList<>();

        Set<String> uniqueTerms = new LinkedHashSet<>();
        for (String term : positiveTerms) {
            if (term != null) {
                String trimmed = term.trim().toLowerCase();
                if (!trimmed.isBlank()) {
                    uniqueTerms.add(trimmed);
                }
            }
        }

        for (String term : uniqueTerms) {
            int idx = 0;
            while ((idx = lower.indexOf(term, idx)) != -1) {
                ranges.add(new Range(idx, idx + term.length()));
                idx += term.length();
            }
        }

        if (ranges.isEmpty()) {
            return HtmlUtils.htmlEscape(raw);
        }

        // Merge overlapping ranges
        ranges.sort((a, b) -> Integer.compare(a.start(), b.start()));
        List<Range> merged = new ArrayList<>();
        Range current = ranges.get(0);
        for (int i = 1; i < ranges.size(); i++) {
            Range next = ranges.get(i);
            if (next.start() <= current.end()) {
                current = new Range(current.start(), Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Range r : merged) {
            out.append(HtmlUtils.htmlEscape(raw.substring(cursor, r.start())));
            out.append("<mark>");
            out.append(HtmlUtils.htmlEscape(raw.substring(r.start(), r.end())));
            out.append("</mark>");
            cursor = r.end();
        }
        if (cursor < raw.length()) {
            out.append(HtmlUtils.htmlEscape(raw.substring(cursor)));
        }
        return out.toString();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private record ParsedQuery(List<String> positiveTerms, List<String> negativeTerms) {
    }

    private record ScoredHit(CivilServiceListRecord entry, int score) {
    }

    private record Range(int start, int end) {
    }

    // SearchHit and SearchResponse have been moved to DTO package classes

    /**
     * Resolves certification or estimation using live SODA3 API records retrieved dynamically.
     */
    public CertificationEstimationDto getCertificationOrEstimation(CivilServiceListRecord record) {
        if (record == null) {
            return null;
        }

        String examNo = record.getExamNo();

        // Fetch live certifications from SODA3 on-demand
        List<CivilServiceRecord> rawCerts = syncService.fetchCertificationsByExamNo(examNo);

        // Filter the fetched certifications list in-memory to ensure precise numeric matching on exam number and certified status
        List<CivilServiceRecord> matchedCerts = rawCerts.stream()
                .filter(c -> isMatchingExam(c.getExamNo(), examNo) && c.getCertDate() != null)
                .toList();

        // Sort by list number ascending
        List<CivilServiceRecord> sortedCerts = new ArrayList<>(matchedCerts);
        sortedCerts.sort((a, b) -> {
            if (a.getListNo() == null && b.getListNo() == null) return 0;
            if (a.getListNo() == null) return 1;
            if (b.getListNo() == null) return -1;
            return a.getListNo().compareTo(b.getListNo());
        });

        // Filter out skips: as soon as a record's listNo is more than 1.5 greater than the previous listNo, it is a skip
        List<CivilServiceRecord> examCerts = new ArrayList<>();
        if (!sortedCerts.isEmpty()) {
            examCerts.add(sortedCerts.get(0));
            for (int i = 1; i < sortedCerts.size(); i++) {
                CivilServiceRecord prev = sortedCerts.get(i - 1);
                CivilServiceRecord curr = sortedCerts.get(i);
                if (prev.getListNo() != null && curr.getListNo() != null) {
                    BigDecimal diff = curr.getListNo().subtract(prev.getListNo());
                    if (diff.compareTo(new BigDecimal("1.5")) > 0) {
                        break;
                    }
                }
                examCerts.add(curr);
            }
        }

        // Calculate historical metrics if examCerts is not empty
        BigDecimal maxReachNumber = BigDecimal.ZERO;
        BigDecimal minReachNumber = null;
        LocalDateTime firstRequestedDate = null;
        LocalDateTime lastRequestedDate = null;

        for (CivilServiceRecord cert : examCerts) {
            if (cert.getListNo() != null) {
                if (cert.getListNo().compareTo(maxReachNumber) > 0) {
                    maxReachNumber = cert.getListNo();
                }
                if (minReachNumber == null || cert.getListNo().compareTo(minReachNumber) < 0) {
                    minReachNumber = cert.getListNo();
                }
            }
            if (cert.getRequestDate() != null) {
                if (firstRequestedDate == null || cert.getRequestDate().isBefore(firstRequestedDate)) {
                    firstRequestedDate = cert.getRequestDate();
                }
                if (lastRequestedDate == null || cert.getRequestDate().isAfter(lastRequestedDate)) {
                    lastRequestedDate = cert.getRequestDate();
                }
            }
        }

        double ratePerDay = 0.0;
        if (firstRequestedDate != null && lastRequestedDate != null) {
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(firstRequestedDate, lastRequestedDate);
            if (daysBetween <= 0) {
                daysBetween = 1;
            }
            double ranksCovered = maxReachNumber.doubleValue() - (minReachNumber != null ? minReachNumber.doubleValue() : 0.0);
            if (ranksCovered > 0) {
                ratePerDay = ranksCovered / (double) daysBetween;
            }
        }

        if (ratePerDay <= 0 && firstRequestedDate != null) {
            long daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(firstRequestedDate, LocalDateTime.now());
            if (daysFromStart > 0) {
                ratePerDay = maxReachNumber.doubleValue() / (double) daysFromStart;
            }
        }

        String reachRateText = ratePerDay > 0 
                ? String.format("%.1f ranks/year", ratePerDay * 365.0)
                : "N/A (Insufficient speed data)";

        // Check if list is expired
        boolean isExpired = isListExpired(record);

        // 1. Search for an exact match in the filtered certification list
        Optional<CivilServiceRecord> exactMatch = examCerts.stream()
                .filter(c -> c.getListNo() != null && c.getListNo().compareTo(record.getListNo()) == 0)
                .findFirst();

        if (exactMatch.isPresent()) {
            CivilServiceRecord cert = exactMatch.get();
            return CertificationEstimationDto.fromCertificate(
                    cert, 
                    isExpired, 
                    maxReachNumber, 
                    firstRequestedDate, 
                    lastRequestedDate, 
                    reachRateText
            );
        }

        // 2. Estimate reach details based on the exam's historical certifications
        if (examCerts.isEmpty()) {
            return CertificationEstimationDto.emptyEstimation("No historical certification data found for this exam number.", isExpired);
        }

        if (minReachNumber == null) {
            minReachNumber = BigDecimal.ZERO;
        }

        double candidateNo = record.getListNo() != null ? record.getListNo().doubleValue() : 0.0;
        double maxReach = maxReachNumber.doubleValue();
        double minReach = minReachNumber.doubleValue();

        // Calculate progress percentage
        double progressPercentage = 0.0;
        if (candidateNo > 0) {
            progressPercentage = (maxReach / candidateNo) * 100.0;
        }
        if (progressPercentage > 100.0) progressPercentage = 100.0;
        if (progressPercentage < 0.0) progressPercentage = 0.0;

        // Calculate remaining ranks to reach
        double remaining = candidateNo - maxReach;
        if (remaining < 0) remaining = 0.0;
        String remainingRanks = String.format("%.3f", remaining);

        Long estimatedDaysToReach = null;
        String estimationStatus = "Insufficient speed data for estimation.";

        if (ratePerDay > 0) {
            double remainingDays = remaining / ratePerDay;
            long daysSinceLastRequest = 0;
            if (lastRequestedDate != null) {
                daysSinceLastRequest = java.time.temporal.ChronoUnit.DAYS.between(lastRequestedDate, LocalDateTime.now());
            }
            long estDaysFromToday = Math.round(remainingDays - daysSinceLastRequest);

            if (estDaysFromToday <= 0) {
                estimatedDaysToReach = 0L;
                if (record.getListNo() != null && record.getListNo().compareTo(maxReachNumber) > 0) {
                    estimationStatus = "Imminent (Soon)";
                } else {
                    estimationStatus = "Imminent (Estimate has already passed)";
                }
            } else {
                estimatedDaysToReach = estDaysFromToday;
                estimationStatus = estDaysFromToday + " days";
            }
        }

        return CertificationEstimationDto.fromEstimation(
                maxReachNumber,
                firstRequestedDate,
                lastRequestedDate,
                estimatedDaysToReach,
                estimationStatus,
                progressPercentage,
                remainingRanks,
                reachRateText,
                isExpired
        );
    }

    private boolean isListExpired(CivilServiceListRecord record) {
        if (record == null) {
            return false;
        }

        // 2. Extension date check
        LocalDateTime currentDate = LocalDateTime.now();
        if (record.getExtensionDate() != null && !record.getExtensionDate().isBlank() && !"N/A".equalsIgnoreCase(record.getExtensionDate())) {
            LocalDateTime extDate = parseDate(record.getExtensionDate());
            if (extDate != null && currentDate.isAfter(extDate)) {
                return true;
            }
        } else if (record.getEstablishedDate() != null) {
            // 3. Established date + 4 years check
            if (currentDate.isAfter(record.getEstablishedDate().plusYears(4))) {
                return true;
            }
        }

        // 4. Newer list published check
        if (record.getListTitleDesc() != null && !record.getListTitleDesc().isBlank() && record.getEstablishedDate() != null) {
            try (SearchSession session = searchMapping.createSession()) {
                List<CivilServiceListRecord> sameTitleRecords = session.search(CivilServiceListRecord.class)
                        .select(CivilServiceListRecord.class)
                        .where(f -> f.match().field("listTitleDesc").matching(record.getListTitleDesc()))
                        .fetchAllHits();
                for (CivilServiceListRecord other : sameTitleRecords) {
                    if (record.getListTitleCode() != null && record.getListTitleCode().equals(other.getListTitleCode())) {
                        if (other.getEstablishedDate() != null && other.getEstablishedDate().isAfter(record.getEstablishedDate())) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error checking list expiration", e);
            }
        }

        return false;
    }

    private LocalDateTime parseDate(String val) {
        if (val == null || val.trim().isEmpty() || "N/A".equalsIgnoreCase(val)) {
            return null;
        }
        val = val.trim();
        try {
            if (val.contains("/")) {
                return java.time.LocalDate.parse(val, java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")).atStartOfDay();
            } else if (val.contains("T")) {
                try {
                    return LocalDateTime.parse(val, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception e) {
                    return java.time.ZonedDateTime.parse(val, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
                }
            } else {
                return java.time.LocalDate.parse(val, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isMatchingExam(String certExam, String candidateExam) {
        if (certExam == null || candidateExam == null) {
            return false;
        }
        try {
            int cExam = Integer.parseInt(certExam.trim());
            int candExam = Integer.parseInt(candidateExam.trim());
            return cExam == candExam;
        } catch (NumberFormatException e) {
            return certExam.trim().equalsIgnoreCase(candidateExam.trim());
        }
    }
}
