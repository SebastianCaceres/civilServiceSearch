package com.civilService.search.config;

import com.civilService.search.job.SyncRecordsJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to define Quartz jobs and triggers.
 */
@Configuration
public class QuartzConfig {

    @Value("${civilservice.sync.cron}")
    private String cronExpression;

    @Bean
    public JobDetail syncRecordsJobDetail() {
        return JobBuilder.newJob(SyncRecordsJob.class)
                .withIdentity("syncRecordsJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger syncRecordsJobTrigger(JobDetail syncRecordsJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(syncRecordsJobDetail)
                .withIdentity("syncRecordsJobTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
    }
}
