package com.payroll.desktop.sync;

import java.util.List;

/** Outcome of pushing the full day-level OT config batch to POST /api/sync/day-level-ot. */
public record DayLevelOtSyncPushResult(int accepted, int updated, int rejected, List<String> rejectedReasons) {
    public int synced() {
        return accepted + updated;
    }
}
