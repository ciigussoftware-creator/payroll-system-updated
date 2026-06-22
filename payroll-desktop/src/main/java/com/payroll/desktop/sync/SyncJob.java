package com.payroll.desktop.sync;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(SyncJob.class);
    static final String SYNC_SERVICE_KEY = "syncService";

    @Override
    public void execute(JobExecutionContext context) {
        SyncService syncService;
        try {
            syncService = (SyncService) context.getScheduler().getContext().get(SYNC_SERVICE_KEY);
        } catch (SchedulerException e) {
            LOG.error("Failed to retrieve SyncService from scheduler context", e);
            return;
        }

        try {
            SyncRunResult result = syncService.syncUnsyncedRecords();
            LOG.info("Scheduled sync complete — attempted={} synced={} failed={} skippedOffline={}",
                    result.attempted(), result.synced(), result.failed(), result.skippedOffline());
        } catch (Exception e) {
            // A failed sync is handled inside SyncService (records stay unsynced, retried next run).
            // Only unexpected errors reach here — log them and keep the scheduler alive.
            LOG.error("Unexpected error during scheduled sync — job suppressed to keep scheduler alive", e);
        }
    }
}
