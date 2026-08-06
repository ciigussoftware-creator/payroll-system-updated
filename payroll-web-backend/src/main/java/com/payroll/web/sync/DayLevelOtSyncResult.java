package com.payroll.web.sync;

import java.time.LocalDate;

public record DayLevelOtSyncResult(
        LocalDate configDate,
        SyncStatus status,
        String reason
) {
    static DayLevelOtSyncResult accepted(LocalDate configDate) {
        return new DayLevelOtSyncResult(configDate, SyncStatus.ACCEPTED, null);
    }

    static DayLevelOtSyncResult updated(LocalDate configDate) {
        return new DayLevelOtSyncResult(configDate, SyncStatus.UPDATED, null);
    }

    static DayLevelOtSyncResult rejected(LocalDate configDate, String reason) {
        return new DayLevelOtSyncResult(configDate, SyncStatus.REJECTED, reason);
    }
}
