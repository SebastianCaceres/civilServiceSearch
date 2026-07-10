package com.civilService.search.job;

import com.civilService.search.service.CivilServiceListSyncService;
import com.civilService.search.service.LuceneIndexService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * Quartz Job to handle periodic tasks. Certifications sync is performed dynamically on-demand.
 */
@Component
@DisallowConcurrentExecution
@Slf4j
public class SyncRecordsJob extends QuartzJobBean {

    private final CivilServiceListSyncService listSyncService;
    private final LuceneIndexService luceneIndexService;

    public SyncRecordsJob(CivilServiceListSyncService listSyncService, LuceneIndexService luceneIndexService) {
        this.listSyncService = listSyncService;
        this.luceneIndexService = luceneIndexService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("SyncRecordsJob trigger fired. Starting background candidates lists synchronization...");
        try {
            boolean synced = listSyncService.syncAllLists();
            if (synced) {
                log.info("New list records synchronized in background. Triggering Lucene index rebuild...");
                luceneIndexService.reindexAll();
                log.info("Lucene index rebuilt successfully in background.");
            } else {
                log.info("No candidates list updates detected. Lucene index rebuild skipped.");
            }
        } catch (Exception e) {
            log.error("Failed to run periodic candidates lists synchronization background job", e);
            throw new JobExecutionException(e);
        }
    }
}
