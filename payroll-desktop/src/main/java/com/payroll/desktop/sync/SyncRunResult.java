package com.payroll.desktop.sync;

public record SyncRunResult(
        int employeesSynced,
        int employeesFailed,
        int workingDaysSynced,
        int workingDaysFailed,
        int otConfigsSynced,
        int otConfigsFailed,
        int otAuthsSynced,
        int otAuthsFailed,
        int attempted,
        int synced,
        int failed,
        boolean skippedOffline,
        boolean attendanceSkippedDueToEmployeeFailure,
        boolean attendanceSkippedDueToOtPushFailure
) {}
