package com.payroll.web.sync;

public record EmployeeSyncResult(
        String employeeCode,
        SyncStatus status,
        String reason
) {
    static EmployeeSyncResult accepted(String employeeCode) {
        return new EmployeeSyncResult(employeeCode, SyncStatus.ACCEPTED, null);
    }

    static EmployeeSyncResult updated(String employeeCode) {
        return new EmployeeSyncResult(employeeCode, SyncStatus.UPDATED, null);
    }

    static EmployeeSyncResult rejected(String employeeCode, String reason) {
        return new EmployeeSyncResult(employeeCode, SyncStatus.REJECTED, reason);
    }
}
