package com.payroll.desktop.sync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression coverage for the app-startup crash that shipped alongside the first
 * SYNC_API_KEY fail-fast change: PayrollApp.start() builds a RealCloudSyncClient,
 * SyncService and SyncScheduler unconditionally on every launch — including for
 * operators doing purely local work (employees, attendance) with no intention of
 * syncing that session. None of that construction, nor starting the Quartz scheduler,
 * may touch SYNC_API_KEY — only an actual sync attempt (Sync Now / the daily job
 * firing) may. Repositories are null here since this exercises construction only,
 * mirroring PayrollApp.java lines 57-58 without needing a real database.
 */
class SyncStartupWiringTest {

    @Test
    void syncWiringConstructsAndStartsWithoutTouchingSyncApiKey() {
        assertThatCode(() -> {
            SyncService syncService = new SyncService(null, null, null, null, null, new RealCloudSyncClient());
            SyncScheduler scheduler = new SyncScheduler(syncService);
            scheduler.start();
            scheduler.shutdown();
        }).doesNotThrowAnyException();
    }
}
