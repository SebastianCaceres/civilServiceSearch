package com.civilService.search.runner;

import com.civilService.search.service.CivilServiceListSyncService;
import com.civilService.search.repository.CivilServiceListRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runner that executes an initial sync of candidate list records on application startup.
 */
@Component
@Slf4j
public class StartupSyncRunner implements CommandLineRunner {

    private final CivilServiceListSyncService listSyncService;
    private final CivilServiceListRecordRepository listRepository;
    private final com.civilService.search.service.LuceneIndexService luceneIndexService;

    @Value("${civilservice.sync.list.limit}")
    private int syncListLimit;

    public StartupSyncRunner(CivilServiceListSyncService listSyncService,
                             CivilServiceListRecordRepository listRepository,
                             com.civilService.search.service.LuceneIndexService luceneIndexService) {
        this.listSyncService = listSyncService;
        this.listRepository = listRepository;
        this.luceneIndexService = luceneIndexService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking initial database state on startup...");
        boolean needReindex = false;

        // ----------------- Candidate Lists (Civil Service) Synchronization -----------------
        log.info("Querying candidate lists counts by status from H2/PostgreSQL...");
        long localActiveCount = listRepository.countByStatus("active");
        long localTerminatedCount = listRepository.countByStatus("terminated");
        long listCount = localActiveCount + localTerminatedCount;
        log.info("Candidate lists local counts: active = {}, terminated = {}, total = {}", localActiveCount, localTerminatedCount, listCount);

        if (listCount == 0) {
            log.info("Database is empty. Triggering full candidate lists sync...");
            listSyncService.syncAllLists();
            needReindex = true;
        } else {
            log.info("Database already populated (total = {}). Skipping startup candidate lists sync.", listCount);
        }
        
        // ----------------- Reindexing Lucene Index -----------------
        if (needReindex) {
            log.info("Database modified. Triggering Lucene mass indexing...");
            luceneIndexService.reindexAll();
            log.info("Lucene mass indexing completed successfully.");
        }

        log.info("Initial database state check and sync process completed.");
    }
}
