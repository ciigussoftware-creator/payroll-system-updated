package com.payroll.desktop.sync;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SyncStatusFormatterTest {

    @Test
    void formatsSyncedAndFailedCountsWithTime() {
        SyncRunResult result = new SyncRunResult(2, 0, 1, 0, 5, 0, 3, 0, 5, 4, 1, false, false, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(14, 5));

        assertThat(text).isEqualTo(
                "Employees: 2 synced. Working days: 1 synced. OT data: 8 synced. Attendance: 4 pushed, 1 failed, at 14:05");
    }

    @Test
    void formatsAllSucceededWithZeroFailed() {
        SyncRunResult result = new SyncRunResult(3, 0, 1, 0, 2, 0, 2, 0, 3, 3, 0, false, false, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(9, 30));

        assertThat(text).isEqualTo(
                "Employees: 3 synced. Working days: 1 synced. OT data: 4 synced. Attendance: 3 pushed, 0 failed, at 09:30");
    }

    @Test
    void formatsEmployeeFailedCountWhenSomeEmployeesAreRejected() {
        SyncRunResult result = new SyncRunResult(4, 1, 1, 0, 2, 0, 2, 0, 2, 2, 0, false, false, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(19, 0));

        assertThat(text).isEqualTo(
                "Employees: 4 synced, 1 failed. Working days: 1 synced. OT data: 4 synced. Attendance: 2 pushed, 0 failed, at 19:00");
    }

    @Test
    void formatsOtFailedCountWhenSomeOtRecordsAreRejected() {
        SyncRunResult result = new SyncRunResult(4, 0, 1, 0, 2, 1, 2, 1, 2, 2, 0, false, false, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(19, 0));

        assertThat(text).isEqualTo(
                "Employees: 4 synced. Working days: 1 synced. OT data: 4 synced, 2 failed. Attendance: 2 pushed, 0 failed, at 19:00");
    }

    @Test
    void formatsWorkingDaysFailedCountWhenSomeWorkingDaysRecordsAreRejected() {
        SyncRunResult result = new SyncRunResult(4, 0, 0, 1, 2, 0, 2, 0, 2, 2, 0, false, false, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(19, 0));

        assertThat(text).isEqualTo(
                "Employees: 4 synced. Working days: 0 synced, 1 failed. OT data: 4 synced. Attendance: 2 pushed, 0 failed, at 19:00");
    }

    @Test
    void formatsAttendanceSkippedWhenEmployeeSyncFails() {
        SyncRunResult result = new SyncRunResult(0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, true, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(19, 0));

        assertThat(text).isEqualTo(
                "Employees: 0 synced, 5 failed. Working days: 0 synced. OT data: 0 synced. Attendance: skipped (employee sync failed), at 19:00");
    }

    @Test
    void formatsAttendanceSkippedWhenOtPushFails() {
        SyncRunResult result = new SyncRunResult(3, 0, 1, 0, 0, 3, 0, 0, 0, 0, 0, false, false, true);

        String text = SyncStatusFormatter.format(result, LocalTime.of(19, 0));

        assertThat(text).isEqualTo(
                "Employees: 3 synced. Working days: 1 synced. OT data: 0 synced, 3 failed. Attendance: skipped (OT sync failed), at 19:00");
    }

    @Test
    void formatsOfflineResultRegardlessOfCounts() {
        SyncRunResult result = new SyncRunResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false);

        String text = SyncStatusFormatter.format(result, LocalTime.of(10, 0));

        assertThat(text).isEqualTo("Last sync: offline — no cloud connection");
    }
}
