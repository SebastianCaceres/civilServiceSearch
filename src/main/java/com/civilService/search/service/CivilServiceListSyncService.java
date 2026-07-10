package com.civilService.search.service;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.repository.CivilServiceListRecordRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.InputStream;
import com.civilService.search.entity.SyncMetadata;
import com.civilService.search.repository.SyncMetadataRepository;

@Service
@Slf4j
public class CivilServiceListSyncService {

    private final CivilServiceListRecordRepository repository;
    private final RestClient restClient;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final SyncMetadataRepository syncMetadataRepository;

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

    public CivilServiceListSyncService(CivilServiceListRecordRepository repository, RestClient.Builder restClientBuilder, EntityManager entityManager, JdbcTemplate jdbcTemplate, SyncMetadataRepository syncMetadataRepository) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.syncMetadataRepository = syncMetadataRepository;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "CivilServiceSearchApp/1.0")
                .build();
    }

    /**
     * Synchronizes active candidates from SODA3.
     */
    @Transactional
    public boolean syncActiveLists() {
        return syncListDataset(activeDatasetId, "active");
    }

    /**
     * Synchronizes terminated candidates from SODA3.
     */
    @Transactional
    public boolean syncTerminatedLists() {
        return syncListDataset(terminatedDatasetId, "terminated");
    }

    /**
     * Runs both active and terminated candidates sync.
     */
    @Transactional
    public boolean syncAllLists() {
        log.info("Starting synchronization of all civil service list records (active and terminated)...");
        boolean terminatedSynced = syncTerminatedLists();
        boolean activeSynced = syncActiveLists();
        log.info("Synchronization of all list records complete!");
        return terminatedSynced || activeSynced;
    }

    @Transactional
    boolean syncListDataset(String datasetId, String targetStatus) {
        log.info("Starting sync of civil service {} list records from SODA CSV...", targetStatus);
        long startTime = System.currentTimeMillis();

        log.info("Checking remote metadata update timestamp for dataset ID: {}", datasetId);
        Long remoteTimestamp = getRemoteUpdateTimestamp(datasetId);
        if (remoteTimestamp != null) {
            Optional<SyncMetadata> localMetadataOpt = syncMetadataRepository.findById(datasetId);
            long localCount = repository.countByStatus(targetStatus);
            if (localCount > 0 && localMetadataOpt.isPresent()) {
                Long localTimestamp = localMetadataOpt.get().getLastSyncedRowsUpdatedAt();
                if (localTimestamp != null && remoteTimestamp <= localTimestamp) {
                    log.info("Dataset {} has not been updated since last sync (local: {}, remote: {}) and database has records. Skipping synchronization.",
                            datasetId, localTimestamp, remoteTimestamp);
                    return false;
                }
            }
        }

        try (InputStream is = getInputStreamForDataset(datasetId)) {
            // Bulk delete first
            log.info("Performing bulk delete for status: {}", targetStatus);
            repository.deleteByStatus(targetStatus);
            entityManager.flush();
            entityManager.clear();

            // Set up Jackson CSV mapper
            CsvMapper mapper = new CsvMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

            CsvSchema schema = CsvSchema.emptySchema().withHeader();

            int insertedCount = 0;
            int skippedCount = 0;
            int totalFetched = 0;

            record ListKey(String examNo, java.math.BigDecimal listNo, String listAgencyCode, String firstName, String lastName) {}
            java.util.Set<ListKey> processedKeys = new java.util.HashSet<>();

            try (MappingIterator<CivilServiceListRecord> it = mapper.readerFor(CivilServiceListRecord.class).with(schema).readValues(is)) {
                List<CivilServiceListRecord> toSave = new ArrayList<>();

                while (it.hasNext()) {
                    if (totalFetched >= syncLimit) {
                        log.info("Reached configured list sync limit of {}. Stopping CSV download.", syncLimit);
                        break;
                    }

                    CivilServiceListRecord record = it.next();
                    totalFetched++;

                    // Check composite key validity
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

                    // Normalize key fields to prevent unique constraint violations
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

                    ListKey key = new ListKey(examNo, listNo, agencyCode, firstName, lastName);
                    if (!processedKeys.add(key)) {
                        skippedCount++;
                        if (skippedCount <= 5) {
                            log.warn("Skipped record due to duplicate key in current batch: {}", key);
                        }
                        continue;
                    }

                    record.setStatus(targetStatus);
                    toSave.add(record);
                    insertedCount++;

                    if (toSave.size() >= batchSize) {
                        log.info("Saving batch of {} records to database ({})...", toSave.size(), targetStatus);
                        deleteConflictingRecords(toSave);
                        repository.saveAll(toSave);
                        repository.flush();
                        entityManager.clear();
                        toSave.clear();
                    }
                }

                // Save remaining records
                if (!toSave.isEmpty()) {
                    log.info("Saving final batch of {} records to database ({})...", toSave.size(), targetStatus);
                    deleteConflictingRecords(toSave);
                    repository.saveAll(toSave);
                    repository.flush();
                    entityManager.clear();
                }
            }

            // Save the new remote timestamp after successful sync
            if (remoteTimestamp != null) {
                syncMetadataRepository.save(new SyncMetadata(datasetId, remoteTimestamp));
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Sync of {} complete! Duration: {} ms. Total processed: {}, Inserted: {}, Skipped: {}.",
                    targetStatus, duration, totalFetched, insertedCount, skippedCount);
            return true;

        } catch (Exception e) {
            log.error("Error occurred while syncing " + targetStatus + " list records via SODA CSV", e);
            throw new RuntimeException(e);
        }
    }

    private InputStream getInputStreamForDataset(String datasetId) throws Exception {
        URI csvUri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("data.cityofnewyork.us")
                .path("/resource/" + datasetId + ".csv")
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

    private void deleteConflictingRecords(List<CivilServiceListRecord> records) {
        String sql = "DELETE FROM civil_service_list_records WHERE exam_no = ? AND list_no = ? AND list_agency_code = ? AND first_name = ? AND last_name = ?";
        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                CivilServiceListRecord record = records.get(i);
                ps.setString(1, record.getExamNo());
                ps.setBigDecimal(2, record.getListNo());
                ps.setString(3, record.getListAgencyCode());
                ps.setString(4, record.getFirstName());
                ps.setString(5, record.getLastName());
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    @Data
    public static class MetadataResponse {
        private String id;
        private Long rowsUpdatedAt;
        private Long updatedAt;
    }
}
