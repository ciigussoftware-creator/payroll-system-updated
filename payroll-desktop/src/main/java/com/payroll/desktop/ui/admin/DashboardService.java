package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes Dashboard data for a given day: that day's scans, the list of employees
 * whose last scan that day was an ENTRY (currently-in for today, missing-clock-out
 * for a past day), and summary counts.
 */
public class DashboardService {

    private final AttendanceRecordRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public DashboardService(AttendanceRecordRepository attendanceRepository,
                            EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    public DashboardSnapshot loadFor(LocalDate date) {
        List<AttendanceRecord> dayScans = attendanceRepository.findByDateRange(date, date);
        int activeEmployeeCount = employeeRepository.findAllActive().size();

        Map<Long, List<AttendanceRecord>> byEmployee = dayScans.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        List<AttendanceRecord> lastScanIsEntry = byEmployee.values().stream()
                .map(scans -> scans.stream()
                        .max(Comparator.comparing(AttendanceRecord::getScanDatetime))
                        .orElseThrow())
                .filter(last -> last.getScanType() == ScanType.ENTRY)
                .sorted(Comparator.comparing(AttendanceRecord::getScanDatetime))
                .collect(Collectors.toList());

        List<AttendanceRecord> scansNewestFirst = dayScans.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getScanDatetime).reversed())
                .collect(Collectors.toList());

        return new DashboardSnapshot(date, scansNewestFirst, lastScanIsEntry,
                activeEmployeeCount, byEmployee.size());
    }
}
