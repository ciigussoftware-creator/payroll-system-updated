package com.payroll.desktop.sync;

import java.util.List;

/** Outcome of pushing the full working-days config batch to POST /api/sync/working-days. */
public record WorkingDaysSyncPushResult(int accepted, int updated, int rejected, List<String> rejectedReasons) {
    public int synced() {
        return accepted + updated;
    }
}
