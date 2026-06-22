package com.payroll.desktop.sync;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SyncScheduler.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

    public static final String DAILY_SYNC_CRON = "0 0 19 * * ?";
    static final String TRIGGER_NAME = "dailySyncTrigger";
    static final String GROUP = "payroll";
    private static final String JOB_NAME = "syncJob";

    private final SyncService syncService;
    private Scheduler quartz;

    public SyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    public void start() throws SchedulerException {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName",
                "PayrollSyncScheduler-" + INSTANCE_COUNTER.getAndIncrement());
        props.setProperty("org.quartz.threadPool.threadCount", "1");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        quartz = new StdSchedulerFactory(props).getScheduler();
        quartz.getContext().put(SyncJob.SYNC_SERVICE_KEY, syncService);

        JobDetail job = JobBuilder.newJob(SyncJob.class)
                .withIdentity(JOB_NAME, GROUP)
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_NAME, GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(DAILY_SYNC_CRON))
                .build();

        quartz.scheduleJob(job, trigger);
        quartz.start();
        LOG.info("SyncScheduler started — daily sync cron: {}", DAILY_SYNC_CRON);
    }

    /** Runs the sync immediately and returns the result. Safe to call while offline. */
    public SyncRunResult triggerNow() {
        LOG.info("Manual sync triggered");
        SyncRunResult result = syncService.syncUnsyncedRecords();
        LOG.info("Manual sync complete — attempted={} synced={} failed={} skippedOffline={}",
                result.attempted(), result.synced(), result.failed(), result.skippedOffline());
        return result;
    }

    public void shutdown() throws SchedulerException {
        if (quartz != null && !quartz.isShutdown()) {
            quartz.shutdown(true);
            LOG.info("SyncScheduler shut down");
        }
    }

    Scheduler getQuartzScheduler() {
        return quartz;
    }
}
