package com.payroll.desktop.sync;

public record SyncRunResult(
        int employeesSynced,
        int employeesFailed,
        int attempted,
        int synced,
        int failed,
        boolean skippedOffline,
        boolean attendanceSkippedDueToEmployeeFailure
) {}
