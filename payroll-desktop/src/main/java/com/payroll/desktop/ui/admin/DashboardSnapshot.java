package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard data for a single day.
 *
 * @param date                the day this snapshot covers
 * @param scans               that day's scans, newest first
 * @param lastScanIsEntry     employees whose last scan that day was an ENTRY — currently-in
 *                            when {@code date} is today, missing-clock-out for a past day
 * @param activeEmployeeCount total active employees (not day-specific)
 * @param scannedEmployeeCount number of distinct employees with at least one scan that day
 */
public record DashboardSnapshot(
        LocalDate date,
        List<AttendanceRecord> scans,
        List<AttendanceRecord> lastScanIsEntry,
        int activeEmployeeCount,
        int scannedEmployeeCount) {
}
