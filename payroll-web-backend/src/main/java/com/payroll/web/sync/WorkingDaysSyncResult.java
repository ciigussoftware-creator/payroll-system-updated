package com.payroll.web.sync;

public record WorkingDaysSyncResult(
        String periodMonth,
        SyncStatus status,
        String reason
) {
    static WorkingDaysSyncResult accepted(String periodMonth) {
        return new WorkingDaysSyncResult(periodMonth, SyncStatus.ACCEPTED, null);
    }

    static WorkingDaysSyncResult updated(String periodMonth) {
        return new WorkingDaysSyncResult(periodMonth, SyncStatus.UPDATED, null);
    }

    static WorkingDaysSyncResult rejected(String periodMonth, String reason) {
        return new WorkingDaysSyncResult(periodMonth, SyncStatus.REJECTED, reason);
    }
}
