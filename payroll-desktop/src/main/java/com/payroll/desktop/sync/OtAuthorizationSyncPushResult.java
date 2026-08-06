package com.payroll.desktop.sync;

import java.util.List;

/** Outcome of pushing the full OT authorization batch to POST /api/sync/ot-authorizations. */
public record OtAuthorizationSyncPushResult(int accepted, int updated, int rejected, List<String> rejectedReasons) {
    public int synced() {
        return accepted + updated;
    }
}
