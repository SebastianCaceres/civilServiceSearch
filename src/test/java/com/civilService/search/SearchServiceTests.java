package com.civilService.search;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.entity.CivilServiceRecord;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import com.civilService.search.service.CivilServiceSyncService;
import com.civilService.search.service.CivilServiceListSyncService;
import com.civilService.search.service.SearchService;
import com.civilService.search.dto.SearchHitDto;
import com.civilService.search.dto.SearchResponseDto;
import com.civilService.search.dto.CertificationEstimationDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void highlightDoesNotCorruptMarkTagsForSingleLetterTerms() {
        SearchMapping searchMapping = mock(SearchMapping.class);
        SearchSession session = mock(SearchSession.class);
        when(searchMapping.createSession()).thenReturn(session);

        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, mock(CivilServiceListSyncService.class));

        CivilServiceListRecord entry = new CivilServiceListRecord();
        entry.setId(1L);
        entry.setFirstName("PINAKIN");
        entry.setMi("R");
        entry.setLastName("PATEL");
        entry.setListTitleDesc("ADMINISTRATIVE PROJECT MANAGER");
        entry.setListAgencyDesc("OPEN COMPETITIVE");
        entry.setExamNo("5164");
        entry.setListNo(new BigDecimal("1413.000"));
        entry.setStatus("active");

        var selectStep = mock(org.hibernate.search.engine.search.query.dsl.SearchQuerySelectStep.class);
        var whereStep = mock(org.hibernate.search.engine.search.query.dsl.SearchQueryWhereStep.class);
        var optionsStep = mock(org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep.class);

        when(session.search(any(Class.class))).thenReturn(selectStep);
        when(selectStep.select(any(Class.class))).thenReturn(whereStep);
        when(whereStep.where(any(java.util.function.Function.class))).thenReturn(optionsStep);
        when(optionsStep.fetchAllHits()).thenReturn(List.of(entry));

        SearchResponseDto response = searchService.searchEntries("PINAKIN R PATEL");

        assertThat(response.totalCount()).isEqualTo(1);
        String fullNameHtml = response.results().get(0).fullNameHtml();
        assertThat(fullNameHtml).contains("<mark>PINAKIN</mark>");
        assertThat(fullNameHtml).contains("<mark>R</mark>");
        assertThat(fullNameHtml).contains("<mark>PATEL</mark>");
        assertThat(fullNameHtml).doesNotContain("ma<mark>rk");
        assertThat(fullNameHtml).doesNotContain("<ma<mark>");
    }

    @Test
    void getCertificationOrEstimationReturnsExactCertificationIfFound() {
        SearchMapping searchMapping = mock(SearchMapping.class);
        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, mock(CivilServiceListSyncService.class));

        CivilServiceListRecord candidate = new CivilServiceListRecord();
        candidate.setExamNo("8042");
        candidate.setListNo(new BigDecimal("12.00"));

        CivilServiceRecord certification = new CivilServiceRecord();
        certification.setExamNo("8042");
        certification.setListNo(new BigDecimal("12.00"));
        certification.setListAgencyDesc("POLICE DEPARTMENT");
        certification.setCertDate(LocalDateTime.now());

        when(syncService.fetchCertificationsByExamNo("8042"))
                .thenReturn(List.of(certification));

        CertificationEstimationDto result = searchService.getCertificationOrEstimation(candidate);

        assertThat(result.isHasCertificate()).isTrue();
        assertThat(result.getCertificate()).isEqualTo(certification);
    }

    @Test
    void getCertificationOrEstimationCalculatesLinearRegressionEstimations() {
        SearchMapping searchMapping = mock(SearchMapping.class);
        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, mock(CivilServiceListSyncService.class));

        CivilServiceListRecord candidate = new CivilServiceListRecord();
        candidate.setId(3L);
        candidate.setExamNo("1234");
        candidate.setListNo(new BigDecimal("15.00")); // Candidate is rank 15

        LocalDateTime firstRequestDate = LocalDateTime.now().minusDays(100);

        // Historical data points: 
        // Rank 10 requested at firstRequestDate (offset 0)
        // Rank 11.5 requested at firstRequestDate + 3 days (offset 3)
        // No skip (difference is 1.5, which is valid)
        CivilServiceRecord cert1 = new CivilServiceRecord();
        cert1.setExamNo("1234");
        cert1.setListNo(new BigDecimal("10.00"));
        cert1.setRequestDate(firstRequestDate);
        cert1.setCertDate(firstRequestDate);

        CivilServiceRecord cert2 = new CivilServiceRecord();
        cert2.setExamNo("1234");
        cert2.setListNo(new BigDecimal("11.50"));
        cert2.setRequestDate(firstRequestDate.plusDays(3)); // 3 days elapsed for 1.5 ranks (2 days per rank)
        cert2.setCertDate(firstRequestDate.plusDays(3));

        when(syncService.fetchCertificationsByExamNo("1234")).thenReturn(List.of(cert1, cert2));

        CertificationEstimationDto result = searchService.getCertificationOrEstimation(candidate);

        assertThat(result.isHasCertificate()).isFalse();
        assertThat(result.getMaxReachNumber()).isEqualTo(new BigDecimal("11.50"));
        assertThat(result.getFirstRequestedDate()).isEqualTo(firstRequestDate);
        assertThat(result.getLastRequestedDate()).isEqualTo(firstRequestDate.plusDays(3));
        
        // Regression calculation: 
        // slope m = (y2 - y1)/(x2 - x1) = (3 - 0)/(2.0 - 1.0) = 3/1 = 3.0 (3 days per rank)
        // intercept c = y1 - m * x1 = 0 - 3.0 * 1 = -3.0
        // Candidate listNo is 15.00, scaled to candidateX = 15.00 * (2.0 / 11.50) = 2.6087
        // estDaysFromStart = m * 2.6087 + c = 7.826 - 3.0 = 4.826 days from firstRequestDate
        // Since firstRequestDate was 100 days ago, estDaysFromToday = 4.826 - 100 = -95 days (already passed/imminent)
        assertThat(result.getEstimatedDaysToReach()).isEqualTo(0L);
        assertThat(result.getEstimationStatus()).contains("Imminent");
    }

    @Test
    void getCertificationOrEstimationFiltersOutSkipsCorrectly() {
        SearchMapping searchMapping = mock(SearchMapping.class);
        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, mock(CivilServiceListSyncService.class));

        CivilServiceListRecord candidate = new CivilServiceListRecord();
        candidate.setId(4L);
        candidate.setExamNo("5555");
        candidate.setListNo(new BigDecimal("10.00"));

        LocalDateTime baseDate = LocalDateTime.now().minusDays(10);

        // Sorted order should be: 1.0 -> 2.0 -> 3.5 -> 10.0 (skip at 10.0 because 10.0 - 3.5 = 6.5 > 1.5)
        CivilServiceRecord cert1 = new CivilServiceRecord();
        cert1.setExamNo("5555");
        cert1.setListNo(new BigDecimal("2.00"));
        cert1.setRequestDate(baseDate.plusDays(1));
        cert1.setCertDate(baseDate.plusDays(1));

        CivilServiceRecord cert2 = new CivilServiceRecord();
        cert2.setExamNo("5555");
        cert2.setListNo(new BigDecimal("1.00"));
        cert2.setRequestDate(baseDate);
        cert2.setCertDate(baseDate);

        CivilServiceRecord cert3 = new CivilServiceRecord();
        cert3.setExamNo("5555");
        cert3.setListNo(new BigDecimal("3.50"));
        cert3.setRequestDate(baseDate.plusDays(2));
        cert3.setCertDate(baseDate.plusDays(2));

        CivilServiceRecord cert4 = new CivilServiceRecord();
        cert4.setExamNo("5555");
        cert4.setListNo(new BigDecimal("10.00")); // Skip!
        cert4.setRequestDate(baseDate.plusDays(5));
        cert4.setCertDate(baseDate.plusDays(5));

        when(syncService.fetchCertificationsByExamNo("5555")).thenReturn(List.of(cert1, cert2, cert3, cert4));

        CertificationEstimationDto result = searchService.getCertificationOrEstimation(candidate);

        // The list should be truncated to [1.0, 2.0, 3.5]. cert4 (10.0) is discarded.
        assertThat(result.isHasCertificate()).isFalse();
        assertThat(result.getMaxReachNumber()).isEqualTo(new BigDecimal("3.50"));
        assertThat(result.getLastRequestedDate()).isEqualTo(baseDate.plusDays(2)); // cert3's request date
    }

    @Test
    void getCertificationOrEstimationWithTestData5054() throws Exception {
        SearchMapping searchMapping = mock(SearchMapping.class);
        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, mock(CivilServiceListSyncService.class));

        CivilServiceListRecord candidate = new CivilServiceListRecord();
        candidate.setId(999L);
        candidate.setExamNo("5054");
        candidate.setListNo(new BigDecimal("476.00"));

        List<CivilServiceRecord> mockCerts = loadMockCertificationsFromCsv();
        when(syncService.fetchCertificationsByExamNo("5054")).thenReturn(mockCerts);

        CertificationEstimationDto result = searchService.getCertificationOrEstimation(candidate);

        // Verify that maxReachNumber matches 344.000 (no skips > 1.5 in entire dataset)
        assertThat(result.isHasCertificate()).isFalse();
        assertThat(result.getMaxReachNumber()).isEqualTo(new BigDecimal("344.000"));
        assertThat(result.getEstimatedDaysToReach()).isNotNull();
    }

    private List<CivilServiceRecord> loadMockCertificationsFromCsv() throws Exception {
        List<CivilServiceRecord> certs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/test-data/testData-5054.csv")))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (parts.length >= 10) {
                    CivilServiceRecord cert = new CivilServiceRecord();
                    cert.setExamNo(parts[0].replace("\"", "").trim());
                    cert.setListNo(new BigDecimal(parts[1].replace("\"", "").trim()));
                    
                    String dateStr = parts[9].replace("\"", "").trim();
                    if (!dateStr.isEmpty()) {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
                        java.time.LocalDate localDate = java.time.LocalDate.parse(dateStr, formatter);
                        cert.setRequestDate(localDate.atStartOfDay());
                    }
                    if (parts.length >= 13) {
                        String certDateStr = parts[12].replace("\"", "").trim();
                        if (!certDateStr.isEmpty()) {
                            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
                            java.time.LocalDate localDate = java.time.LocalDate.parse(certDateStr, formatter);
                            cert.setCertDate(localDate.atStartOfDay());
                        }
                    }
                    certs.add(cert);
                }
            }
        }
        return certs;
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSearchIsLightweightAndDetailsAreOnDemand() {
        SearchMapping searchMapping = mock(SearchMapping.class);
        SearchSession session = mock(SearchSession.class);
        when(searchMapping.createSession()).thenReturn(session);

        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        CivilServiceListSyncService listSyncService = mock(CivilServiceListSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, listSyncService);

        // Set up mock database record
        CivilServiceListRecord candidate = new CivilServiceListRecord();
        candidate.setId(101L);
        candidate.setFirstName("JOHN");
        candidate.setLastName("DOE");
        candidate.setExamNo("5054");
        candidate.setListNo(new BigDecimal("10.000"));
        
        var selectStep = mock(org.hibernate.search.engine.search.query.dsl.SearchQuerySelectStep.class);
        var whereStep = mock(org.hibernate.search.engine.search.query.dsl.SearchQueryWhereStep.class);
        var optionsStep = mock(org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep.class);

        when(session.search(any(Class.class))).thenReturn(selectStep);
        when(selectStep.select(any(Class.class))).thenReturn(whereStep);
        when(whereStep.where(any(java.util.function.Function.class))).thenReturn(optionsStep);
        when(optionsStep.fetchAllHits()).thenReturn(List.of(candidate));
        when(optionsStep.fetchSingleHit()).thenReturn(Optional.of(candidate));

        // 1. Run Search Results (lightweight search)
        SearchResponseDto searchResponse = searchService.searchEntries("JOHN");

        // Verify basic metadata returned
        assertThat(searchResponse.totalCount()).isEqualTo(1);
        assertThat(searchResponse.results().get(0).fullNameHtml()).contains("JOHN");
        
        // Assert that NO dynamic API calls (syncService) were made during search
        org.mockito.Mockito.verifyNoInteractions(syncService);

        // 2. Fetch Details (on-demand record details)
        CivilServiceListRecord fetched = searchService.getRecordById(101L);
        assertThat(fetched).isNotNull();
        
        // Mock syncService behavior for details view
        CivilServiceRecord cert = new CivilServiceRecord();
        cert.setExamNo("5054");
        cert.setListNo(new BigDecimal("10.000"));
        cert.setCertDate(java.time.LocalDateTime.now());
        cert.setRequestDate(java.time.LocalDateTime.now());
        when(syncService.fetchCertificationsByExamNo("5054")).thenReturn(List.of(cert));

        // Trigger linear regression estimation / dynamic lookups
        CertificationEstimationDto estimation = searchService.getCertificationOrEstimation(fetched);
        
        // Verify syncService WAS called for details view
        org.mockito.Mockito.verify(syncService).fetchCertificationsByExamNo("5054");
        assertThat(estimation.isHasCertificate()).isTrue();
    }

    @Test
    void testGetCertificationOrEstimationWithPayrollMatch() {
        SearchMapping searchMapping = mock(SearchMapping.class);
        CivilServiceSyncService syncService = mock(CivilServiceSyncService.class);
        CivilServiceListSyncService listSyncService = mock(CivilServiceListSyncService.class);
        SearchService searchService = new SearchService(searchMapping, syncService, listSyncService);

        CivilServiceListRecord candidate = new CivilServiceListRecord();
        candidate.setId(202L);
        candidate.setFirstName("MUKESH");
        candidate.setLastName("MASSAND");
        candidate.setListTitleCode("10074");
        candidate.setListAgencyCode("999"); // Set different agency code on candidate list record!
        candidate.setExamNo("5054");
        candidate.setListNo(new BigDecimal("1.000"));

        // Setup mock payroll record
        CivilServiceSyncService.CivilListPayrollRecord mockPayroll = CivilServiceSyncService.CivilListPayrollRecord.builder()
                .calendarYear("2024")
                .dpt("806")
                .name("M MASSAND")
                .agencyName("HOUSING PRESERVATION & DEVELOPMENT")
                .ttl("10074")
                .pc("A")
                .salRate("$68,213.00")
                .build();

        // Stub syncService to return the payroll record (using certification agency code "806")
        when(syncService.fetchPayrollRecords("806", "MUKESH", null, "MASSAND"))
                .thenReturn(List.of(mockPayroll));

        // Mock standard certs lookup to return a certification with listNo matching candidate, certDate in 2023, and listAgencyCode "806"
        CivilServiceRecord mockCert = new CivilServiceRecord();
        mockCert.setExamNo("5054");
        mockCert.setListNo(new BigDecimal("1.000"));
        mockCert.setListAgencyCode("806"); // Agency code comes from the certification!
        mockCert.setCertDate(LocalDateTime.of(2023, 5, 1, 0, 0));
        mockCert.setRequestDate(LocalDateTime.of(2023, 5, 1, 0, 0));
        when(syncService.fetchCertificationsByExamNo("5054")).thenReturn(List.of(mockCert));

        // Run
        CertificationEstimationDto estimation = searchService.getCertificationOrEstimation(candidate);

        // Assertions
        assertThat(estimation.isAppointed()).isTrue();
        assertThat(estimation.getPayrollRecord()).isNotNull();
        assertThat(estimation.getPayrollRecord().getName()).isEqualTo("M MASSAND");
        assertThat(estimation.getPayrollRecord().getSalRate()).isEqualTo("$68,213.00");
        
        // Assert that syncService was queried with the certification's agency code "806", not the candidate's agency code "999"
        org.mockito.Mockito.verify(syncService).fetchPayrollRecords("806", "MUKESH", null, "MASSAND");
    }
}
