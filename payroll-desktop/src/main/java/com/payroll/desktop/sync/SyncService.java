package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.desktop.repository.AttendanceRecordRepository;

import java.util.List;

public class SyncService {

    private final AttendanceRecordRepository recordRepo;
    private final CloudSyncClient client;

    public SyncService(AttendanceRecordRepository recordRepo, CloudSyncClient client) {
        this.recordRepo = recordRepo;
        this.client = client;
    }

    public SyncRunResult syncUnsyncedRecords() {
        if (!client.isCloudReachable()) {
            return new SyncRunResult(0, 0, 0, true);
        }

        List<AttendanceRecord> pending = recordRepo.findUnsynced();
        int attempted = 0, synced = 0, failed = 0;

        for (AttendanceRecord record : pending) {
            attempted++;
            try {
                SyncPushResult result = client.pushRecord(record);
                recordRepo.markSynced(record.getId(), result.cloudRecordId());
                synced++;
            } catch (SyncException e) {
                recordRepo.incrementSyncAttempt(record.getId(), e.getMessage());
                failed++;
            }
        }

        return new SyncRunResult(attempted, synced, failed, false);
    }
}
