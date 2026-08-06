package com.payroll.web.sync;

import java.time.LocalDate;

public record OtAuthorizationSyncResult(
        String employeeCode,
        LocalDate authDate,
        SyncStatus status,
        String reason
) {
    static OtAuthorizationSyncResult accepted(String employeeCode, LocalDate authDate) {
        return new OtAuthorizationSyncResult(employeeCode, authDate, SyncStatus.ACCEPTED, null);
    }

    static OtAuthorizationSyncResult updated(String employeeCode, LocalDate authDate) {
        return new OtAuthorizationSyncResult(employeeCode, authDate, SyncStatus.UPDATED, null);
    }

    static OtAuthorizationSyncResult rejected(String employeeCode, LocalDate authDate, String reason) {
        return new OtAuthorizationSyncResult(employeeCode, authDate, SyncStatus.REJECTED, reason);
    }
}
