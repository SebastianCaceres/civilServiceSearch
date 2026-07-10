package com.civilService.search.service;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.entity.CivilServiceRecord;
import com.civilService.search.repository.CivilServiceListRecordRepository;
import lombok.extern.slf4j.Slf4j;
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

    private final CivilServiceListRecordRepository repository;
    private final CivilServiceSyncService syncService;
    private final CivilServiceListSyncService listSyncService;

    public SearchService(CivilServiceListRecordRepository repository, CivilServiceSyncService syncService, CivilServiceListSyncService listSyncService) {
        this.repository = repository;
        this.syncService = syncService;
        this.listSyncService = listSyncService;
    }

    /**
     * Search for candidate entries matching the query string in Lucene.
     */
    public SearchResponse searchEntries(String query) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(Collections.emptyList(), 0, 0);
        }

        long start = System.currentTimeMillis();
        ParsedQuery parsed = parseQuery(query);

        // Fetch candidate list records matching query using repository
        List<CivilServiceListRecord> dbHits = repository.findAll();
        List<ScoredHit> scored = new ArrayList<>();

        for (CivilServiceListRecord record : dbHits) {
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

        List<SearchHit> results = new ArrayList<>();
        for (ScoredHit hit : scored) {
            CivilServiceListRecord record = hit.entry();
            results.add(new SearchHit(
                    record.getId(),
                    highlight(buildFullName(record), parsed.positiveTerms()),
                    highlight(record.getListTitleDesc(), parsed.positiveTerms()),
                    highlight(record.getListAgencyDesc(), parsed.positiveTerms()),
                    record.getExamNo(),
                    record.getListNo() != null ? record.getListNo().toString() : "",
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

        long took = System.currentTimeMillis() - start;
        return new SearchResponse(results, results.size(), took);
    }

    public CivilServiceListRecord getRecordById(Long id) {
        return repository.findById(id).orElse(null);
    }

    private int scoreRecord(CivilServiceListRecord entry, ParsedQuery parsed) {
        String fullName = buildFullName(entry).toLowerCase();
        String title = safeLower(entry.getListTitleDesc());
        String agency = safeLower(entry.getListAgencyDesc());
        String fullText = (fullName + " " + title + " " + agency).trim();

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

    public record SearchHit(
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

    public record SearchResponse(List<SearchHit> results, int totalCount, long tookMs) {
    }

    /**
     * Resolves certification or estimation using live SODA3 API records retrieved dynamically.
     */
    public CertificationEstimation getCertificationOrEstimation(CivilServiceListRecord record) {
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

        // 1. Search for an exact match in the filtered certification list
        Optional<CivilServiceRecord> exactMatch = examCerts.stream()
                .filter(c -> c.getListNo() != null && c.getListNo().compareTo(record.getListNo()) == 0)
                .findFirst();

        boolean isAppointed = false;
        com.civilService.search.service.CivilServiceSyncService.CivilListPayrollRecord payrollRecord = null;

        if (exactMatch.isPresent()) {
            CivilServiceRecord cert = exactMatch.get();
            LocalDateTime certDate = cert.getCertDate();
            String certAgencyCode = cert.getListAgencyCode(); // Use agency code from the certification!

            if (certAgencyCode != null && certDate != null) {
                // Fetch live payroll records from ye3c-m4ga on-demand using certification agency code
                List<com.civilService.search.service.CivilServiceSyncService.CivilListPayrollRecord> payroll = syncService.fetchPayrollRecords(
                        certAgencyCode,
                        record.getFirstName(),
                        record.getMi(),
                        record.getLastName()
                );

                if (payroll != null) {
                    for (com.civilService.search.service.CivilServiceSyncService.CivilListPayrollRecord pr : payroll) {
                        try {
                            int pYear = Integer.parseInt(pr.getCalendarYear());
                            if (certDate.getYear() < pYear) {
                                isAppointed = true;
                                payrollRecord = pr;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }

            return CertificationEstimation.builder()
                    .hasCertificate(true)
                    .certificate(cert)
                    .isAppointed(isAppointed)
                    .payrollRecord(payrollRecord)
                    .build();
        }

        // 2. Estimate reach details based on the exam's historical certifications
        if (examCerts.isEmpty()) {
            return CertificationEstimation.builder()
                    .hasCertificate(false)
                    .estimationStatus("No historical certification data found for this exam number.")
                    .isAppointed(isAppointed)
                    .payrollRecord(payrollRecord)
                    .build();
        }

        BigDecimal maxReachNumber = BigDecimal.ZERO;
        LocalDateTime firstRequestedDate = null;
        LocalDateTime lastRequestedDate = null;

        // Collect valid data points for linear regression
        record Point(double x, double y) {}
        List<Point> dataPoints = new ArrayList<>();

        // Find min/max request dates and max list number reached
        for (CivilServiceRecord cert : examCerts) {
            if (cert.getListNo() != null) {
                if (cert.getListNo().compareTo(maxReachNumber) > 0) {
                    maxReachNumber = cert.getListNo();
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

        // Second pass: compute days from firstRequestedDate for regression
        if (firstRequestedDate != null) {
            for (int i = 0; i < examCerts.size(); i++) {
                CivilServiceRecord cert = examCerts.get(i);
                if (cert.getListNo() != null && cert.getRequestDate() != null) {
                    double x = (double) (i + 1);
                    double y = java.time.temporal.ChronoUnit.DAYS.between(firstRequestedDate, cert.getRequestDate());
                    dataPoints.add(new Point(x, y));
                }
            }
        }

        Long estimatedDaysToReach = null;
        String estimationStatus = "Insufficient data points for linear regression estimation.";

        if (dataPoints.size() >= 2) {
            double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
            int n = dataPoints.size();
            for (Point p : dataPoints) {
                sumX += p.x;
                sumY += p.y;
                sumXY += p.x * p.y;
                sumXX += p.x * p.x;
            }
            double denominator = (n * sumXX) - (sumX * sumX);
            if (Math.abs(denominator) > 1e-6) {
                double m = ((n * sumXY) - (sumX * sumY)) / denominator;
                double c = (sumY - (m * sumX)) / n;

                double candidateX = record.getListNo() != null ? record.getListNo().doubleValue() : 0.0;
                if (maxReachNumber.compareTo(BigDecimal.ZERO) > 0 && !examCerts.isEmpty()) {
                    candidateX = candidateX * ((double) examCerts.size() / maxReachNumber.doubleValue());
                }
                double estDaysFromStart = (m * candidateX) + c;

                long daysStartToToday = java.time.temporal.ChronoUnit.DAYS.between(firstRequestedDate, LocalDateTime.now());
                long estDaysFromToday = Math.round(estDaysFromStart - daysStartToToday);

                // If candidate has not been reached yet, but the estimate is <= 0,
                // we fall back to a linear rate projection based on actual reached history.
                if (record.getListNo() != null && record.getListNo().compareTo(maxReachNumber) > 0) {
                    if (estDaysFromToday <= 0) {
                        BigDecimal minReachNumber = examCerts.get(0).getListNo() != null ? examCerts.get(0).getListNo() : BigDecimal.ZERO;
                        double ranksCovered = maxReachNumber.doubleValue() - minReachNumber.doubleValue();
                        long timeTaken = java.time.temporal.ChronoUnit.DAYS.between(firstRequestedDate, lastRequestedDate);
                        if (timeTaken <= 0) {
                            timeTaken = 1;
                        }
                        double rate = ranksCovered / timeTaken; // ranks per day
                        if (rate > 0) {
                            double remainingRanks = record.getListNo().doubleValue() - maxReachNumber.doubleValue();
                            double remainingDays = remainingRanks / rate;
                            long daysSinceLastRequest = java.time.temporal.ChronoUnit.DAYS.between(lastRequestedDate, LocalDateTime.now());
                            estDaysFromToday = Math.round(remainingDays - daysSinceLastRequest);
                        }
                    }
                }

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
        }

        return CertificationEstimation.builder()
                .hasCertificate(false)
                .maxReachNumber(maxReachNumber)
                .firstRequestedDate(firstRequestedDate)
                .lastRequestedDate(lastRequestedDate)
                .estimatedDaysToReach(estimatedDaysToReach)
                .estimationStatus(estimationStatus)
                .isAppointed(isAppointed)
                .payrollRecord(payrollRecord)
                .build();
    }



    private boolean isMatchingExam(String certExam, String candidateExam) {
        if (certExam == null || candidateExam == null) {
            return false;
        }
        try {
            return Integer.parseInt(certExam.trim()) == Integer.parseInt(candidateExam.trim());
        } catch (NumberFormatException e) {
            return certExam.trim().equalsIgnoreCase(candidateExam.trim());
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CertificationEstimation {
        private boolean hasCertificate;
        private CivilServiceRecord certificate;
        private BigDecimal maxReachNumber;
        private LocalDateTime firstRequestedDate;
        private LocalDateTime lastRequestedDate;
        private Long estimatedDaysToReach;
        private String estimationStatus;
        private boolean isAppointed;
        private com.civilService.search.service.CivilServiceSyncService.CivilListPayrollRecord payrollRecord;
    }
}
