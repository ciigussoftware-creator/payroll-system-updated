package com.payroll.desktop.sync;

import java.util.List;

/** Outcome of pushing the full employee batch to POST /api/sync/employees. */
public record EmployeeSyncPushResult(int accepted, int updated, int rejected, List<String> rejectedReasons) {
    public int synced() {
        return accepted + updated;
    }
}
