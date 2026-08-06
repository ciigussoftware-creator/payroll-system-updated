package com.payroll.desktop.sync;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Formats a {@link SyncRunResult} into the status text shown next to the "Sync Now" button. */
public final class SyncStatusFormatter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static final String NEVER_SYNCED_THIS_SESSION = "Never synced this session.";
    public static final String SYNCING = "Syncing…";

    private SyncStatusFormatter() {
    }

    public static String format(SyncRunResult result, LocalTime at) {
        if (result.skippedOffline()) {
            return "Last sync: offline — no cloud connection";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Employees: ").append(result.employeesSynced()).append(" synced");
        if (result.employeesFailed() > 0) {
            sb.append(", ").append(result.employeesFailed()).append(" failed");
        }

        int otSynced = result.otConfigsSynced() + result.otAuthsSynced();
        int otFailed = result.otConfigsFailed() + result.otAuthsFailed();
        sb.append(". OT data: ").append(otSynced).append(" synced");
        if (otFailed > 0) {
            sb.append(", ").append(otFailed).append(" failed");
        }

        sb.append(". Attendance: ");
        if (result.attendanceSkippedDueToEmployeeFailure()) {
            sb.append("skipped (employee sync failed)");
        } else if (result.attendanceSkippedDueToOtPushFailure()) {
            sb.append("skipped (OT sync failed)");
        } else {
            sb.append(result.synced()).append(" pushed, ").append(result.failed()).append(" failed");
        }

        sb.append(", at ").append(at.format(TIME_FMT));
        return sb.toString();
    }
}
