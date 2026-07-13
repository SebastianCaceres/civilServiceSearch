package com.civilService.search.service;

import com.civilService.search.entity.CivilServiceRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.List;

/**
 * Service responsible for performing on-demand SODA3 REST API lookups for active/historical certifications.
 */
@Service
@Slf4j
public class CivilServiceSyncService {

    private final RestClient restClient;

    @Value("${civilservice.sync.url}")
    private String syncUrl;

    @Value("${civilservice.sync.app-token:}")
    private String appToken;

    public CivilServiceSyncService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "CivilServiceSearchApp/1.0")
                .build();
    }

    /**
     * Live query: fetch all certifications for a specific exam from NYC Open Data.
     */
    public List<CivilServiceRecord> fetchCertificationsByExamNo(String examNo) {
        try {
            // query: SELECT * WHERE exam_no like '%examNo%' ORDER BY list_no ASC LIMIT 50000
            String soql = "SELECT * WHERE exam_no like '%" + examNo + "%' ORDER BY list_no ASC LIMIT 50000";
            URI queryUri = UriComponentsBuilder.fromUriString(syncUrl)
                    .queryParam("query", soql)
                    .build()
                    .toUri();

            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(queryUri);
            if (appToken != null && !appToken.trim().isEmpty()) {
                spec.header("X-App-Token", appToken.trim());
            }

            CivilServiceRecord[] records = spec.retrieve().body(CivilServiceRecord[].class);
            if (records != null) {
                return List.of(records);
            }
        } catch (Exception e) {
            log.error("Failed to fetch live SODA3 certifications", e);
        }
        return List.of();
    }

    /**
     * Live query: fetch payroll records from NYC Civil List (ye3c-m4ga) for a candidate.
     */
    public List<CivilListPayrollRecord> fetchPayrollRecords(String agencyCode, String firstName, String mi, String lastName) {
        if (agencyCode == null || agencyCode.isBlank() || firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            return List.of();
        }
        try {
            String firstChar = firstName.substring(0, 1).toUpperCase();
            String miPart = (mi == null || mi.trim().isEmpty())
                    ? ""
                    : mi.trim().toUpperCase() + " ";
            String cleanLastName = lastName.trim().toUpperCase();
            String employeeName = firstChar + " " + miPart + cleanLastName;

            // Formulate query using like matching to tolerate spacing variations
            String namePattern = firstChar + "%" + cleanLastName;
            String soql = "SELECT * WHERE dpt = '" + agencyCode.trim() + "' AND (name = '" + employeeName.replace("'", "''") + "' OR name like '" + namePattern.replace("'", "''") + "')";

            URI queryUri = UriComponentsBuilder.fromUriString("https://data.cityofnewyork.us/resource/ye3c-m4ga.json")
                    .queryParam("$query", soql)
                    .build()
                    .toUri();

            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(queryUri);
            if (appToken != null && !appToken.trim().isEmpty()) {
                spec.header("X-App-Token", appToken.trim());
            }

            CivilListPayrollRecord[] records = spec.retrieve().body(CivilListPayrollRecord[].class);
            if (records != null) {
                return List.of(records);
            }
        } catch (Exception e) {
            log.error("Failed to fetch payroll records", e);
        }
        return List.of();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class CivilListPayrollRecord {
        @com.fasterxml.jackson.annotation.JsonProperty("calendar_year")
        private String calendarYear;
        private String dpt;
        private String name;
        @com.fasterxml.jackson.annotation.JsonProperty("agency_name")
        private String agencyName;
        private String ttl;
        private String pc;
        @com.fasterxml.jackson.annotation.JsonProperty("sal_rate")
        private String salRate;
    }
}
