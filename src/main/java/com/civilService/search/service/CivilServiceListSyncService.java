package com.civilService.search.service;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.entity.SyncMetadata;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

@Service
@Slf4j
public class CivilServiceListSyncService {

    private final SearchMapping searchMapping;
    private final RestClient restClient;

    @Value("${civilservice.sync.list.active-dataset-id}")
    private String activeDatasetId;

    @Value("${civilservice.sync.list.terminated-dataset-id}")
    private String terminatedDatasetId;

    @Value("${civilservice.sync.list.limit}")
    private int syncLimit;

    @Value("${civilservice.sync.batch-size:20000}")
    private int batchSize;

    @Value("${civilservice.sync.app-token:}")
    private String appToken;

    public CivilServiceListSyncService(SearchMapping searchMapping, RestClient.Builder restClientBuilder) {
        this.searchMapping = searchMapping;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "CivilServiceSearchApp/1.0")
                .build();
    }

    /**
     * Synchronizes active candidates from SODA3.
     */
    public boolean syncActiveLists() {
        return syncListDataset(activeDatasetId, "active");
    }

    /**
     * Synchronizes terminated candidates from SODA3.
     */
    public boolean syncTerminatedLists() {
        return syncListDataset(terminatedDatasetId, "terminated");
    }

    /**
     * Runs both active and terminated candidates sync.
     */
    public boolean syncAllLists() {
        log.info("Starting synchronization of all civil service list records (active and terminated)...");
        boolean terminatedSynced = syncTerminatedLists();
        boolean activeSynced = syncActiveLists();
        log.info("Synchronization of all list records complete!");
        return terminatedSynced || activeSynced;
    }

    boolean syncListDataset(String datasetId, String targetStatus) {
        log.info("Starting sync of civil service {} list records from SODA CSV...", targetStatus);
        long startTime = System.currentTimeMillis();

        try {
            log.info("Checking remote metadata update timestamp for dataset ID: {}", datasetId);
            Long remoteTimestamp = getRemoteUpdateTimestamp(datasetId);
            if (remoteTimestamp != null) {
                SyncMetadata localMetadata = null;
                long localCount = 0;
                try (SearchSession session = searchMapping.createSession()) {
                    List<SyncMetadata> hits = session.search(SyncMetadata.class)
                            .select(SyncMetadata.class)
                            .where(f -> f.id().matching(datasetId))
                            .fetch(1)
                            .hits();
                    localMetadata = hits.isEmpty() ? null : hits.get(0);

                    localCount = session.search(CivilServiceListRecord.class)
                            .where(f -> f.match().field("status").matching(targetStatus))
                            .fetchTotalHitCount();
                }

                if (localCount > 0 && localMetadata != null) {
                    Long localTimestamp = localMetadata.getLastSyncedRowsUpdatedAt();
                    if (localTimestamp != null && remoteTimestamp <= localTimestamp) {
                        log.info("Dataset {} has not been updated since last sync (local: {}, remote: {}) and database has records. Skipping synchronization.",
                                datasetId, localTimestamp, remoteTimestamp);
                        return false;
                    }
                }
            }

            // 1. Delete old records
            try (SearchSession session = searchMapping.createSession()) {
                log.info("Performing bulk delete in Lucene for status: {}", targetStatus);
                List<CivilServiceListRecord> recordsToDelete = session.search(CivilServiceListRecord.class)
                        .select(CivilServiceListRecord.class)
                        .where(f -> f.match().field("status").matching(targetStatus))
                        .fetchAllHits();
                for (CivilServiceListRecord record : recordsToDelete) {
                    session.indexingPlan().delete(record);
                }
            }

            // 2. Download and insert records in chunks
            int insertedCount = 0;
            int skippedCount = 0;
            int totalFetched = 0;

            try (InputStream is = getInputStreamForDataset(datasetId)) {
                CsvMapper mapper = new CsvMapper();
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                CsvSchema schema = CsvSchema.emptySchema().withHeader();

                SearchSession session = searchMapping.createSession();
                try (MappingIterator<CivilServiceListRecord> it = mapper.readerFor(CivilServiceListRecord.class).with(schema).readValues(is)) {
                    while (it.hasNext()) {
                        if (syncLimit >= 0 && totalFetched >= syncLimit) {
                            log.info("Reached configured list sync limit of {}. Stopping CSV download.", syncLimit);
                            break;
                        }

                        CivilServiceListRecord record = it.next();
                        totalFetched++;

                        if (record.getExamNo() == null || record.getListNo() == null ||
                            record.getListAgencyCode() == null || record.getFirstName() == null ||
                            record.getLastName() == null) {
                            skippedCount++;
                            if (skippedCount <= 5) {
                                log.warn("Skipped record due to null key fields: examNo={}, listNo={}, agencyCode={}, firstName={}, lastName={}",
                                         record.getExamNo(), record.getListNo(), record.getListAgencyCode(), record.getFirstName(), record.getLastName());
                            }
                            continue;
                        }

                        String examNo = record.getExamNo().trim();
                        BigDecimal listNo = record.getListNo().stripTrailingZeros();
                        String agencyCode = record.getListAgencyCode().trim();
                        String firstName = record.getFirstName().trim();
                        String lastName = record.getLastName().trim();

                        record.setExamNo(examNo);
                        record.setListNo(listNo);
                        record.setListAgencyCode(agencyCode);
                        record.setFirstName(firstName);
                        record.setLastName(lastName);
                        if (record.getMi() != null) {
                            record.setMi(record.getMi().trim());
                        }

                        record.setId(generateId(examNo, listNo, agencyCode, firstName, lastName));
                        record.setStatus(targetStatus);

                        session.indexingPlan().add(record);
                        insertedCount++;

                        if (insertedCount % 50000 == 0) {
                            log.info("Flushing Lucene session batch for {} (processed {})...", targetStatus, insertedCount);
                            session.close();
                            session = searchMapping.createSession();
                        }
                    }
                } finally {
                    if (session != null) {
                        session.close();
                    }
                }
            }

            // 3. Save the new remote timestamp
            if (remoteTimestamp != null) {
                try (SearchSession session = searchMapping.createSession()) {
                    session.indexingPlan().add(new SyncMetadata(datasetId, remoteTimestamp));
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Sync of {} complete! Duration: {} ms. Total processed: {}, Inserted: {}, Skipped: {}.",
                    targetStatus, duration, totalFetched, insertedCount, skippedCount);

            // Optimize segment layout after changes are committed:
            try (SearchSession session = searchMapping.createSession()) {
                log.info("Optimizing Lucene index segments for status: {}...", targetStatus);
                session.workspace(CivilServiceListRecord.class).mergeSegments();
                session.workspace(SyncMetadata.class).mergeSegments();
            }
            return true;
        } catch (Exception e) {
            log.error("Error occurred while syncing " + targetStatus + " list records via SODA CSV", e);
            throw new RuntimeException(e);
        }
    }

    private long generateId(String examNo, BigDecimal listNo, String listAgencyCode, String firstName, String lastName) {
        String key = examNo + "|" + listNo.toPlainString() + "|" + listAgencyCode + "|" + firstName + "|" + lastName;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (bytes[i] & 0xff);
            }
            return Math.abs(result);
        } catch (Exception e) {
            return Math.abs((long) key.hashCode());
        }
    }

    private InputStream getInputStreamForDataset(String datasetId) throws Exception {
        int limit = (syncLimit >= 0) ? syncLimit : 2000000;
        URI csvUri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("data.cityofnewyork.us")
                .path("/resource/" + datasetId + ".csv")
                .queryParam("$limit", String.valueOf(limit))
                .build()
                .toUri();

        log.info("Downloading remote SODA CSV from URI: {}", csvUri);
        RestClient.RequestHeadersSpec<?> requestSpec = restClient.get().uri(csvUri);
        if (appToken != null && !appToken.trim().isEmpty()) {
            requestSpec.header("X-App-Token", appToken.trim());
        }
        requestSpec.header(HttpHeaders.ACCEPT, "text/csv");
        return requestSpec.retrieve().body(org.springframework.core.io.Resource.class).getInputStream();
    }

    private Long getRemoteUpdateTimestamp(String datasetId) {
        try {
            URI metadataUri = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host("data.cityofnewyork.us")
                    .path("/api/views/" + datasetId + ".json")
                    .build()
                    .toUri();

            log.info("Fetching SODA dataset metadata from: {}", metadataUri);
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(metadataUri);
            if (appToken != null && !appToken.trim().isEmpty()) {
                spec.header("X-App-Token", appToken.trim());
            }

            MetadataResponse response = spec.retrieve().body(MetadataResponse.class);
            if (response != null) {
                if (response.getRowsUpdatedAt() != null) {
                    return response.getRowsUpdatedAt();
                } else if (response.getUpdatedAt() != null) {
                    return response.getUpdatedAt();
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch metadata for dataset: {}", datasetId, e);
        }
        return null;
    }

    @Data
    public static class MetadataResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("id")
        private String id;
        @com.fasterxml.jackson.annotation.JsonProperty("rowsUpdatedAt")
        private Long rowsUpdatedAt;
        @com.fasterxml.jackson.annotation.JsonProperty("updatedAt")
        private Long updatedAt;
    }
}
