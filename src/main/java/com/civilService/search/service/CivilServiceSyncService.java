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
}
