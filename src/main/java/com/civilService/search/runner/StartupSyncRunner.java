package com.civilService.search.runner;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.service.CivilServiceListSyncService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Runner that executes an initial sync of candidate list records on application startup.
 */
@Component
@ConditionalOnProperty(name = "civilservice.sync.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class StartupSyncRunner implements CommandLineRunner {

    private final CivilServiceListSyncService listSyncService;
    private final SearchMapping searchMapping;

    @Value("${civilservice.sync.list.limit}")
    private int syncListLimit;

    public StartupSyncRunner(CivilServiceListSyncService listSyncService,
                             SearchMapping searchMapping) {
        this.listSyncService = listSyncService;
        this.searchMapping = searchMapping;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking initial database state on startup...");
        listSyncService.syncAllLists();
        log.info("Initial database state check and sync process completed.");
    }
}
