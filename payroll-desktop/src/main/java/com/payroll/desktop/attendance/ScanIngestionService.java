package com.payroll.desktop.attendance;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class ScanIngestionService {

    static final long GUARD_MINUTES = 30;

    private final AttendanceRecordRepository attendanceRepo;
    private final EmployeeRepository employeeRepo;

    public ScanIngestionService(AttendanceRecordRepository attendanceRepo,
                                EmployeeRepository employeeRepo) {
        this.attendanceRepo = attendanceRepo;
        this.employeeRepo = employeeRepo;
    }

    public ScanResult recordScan(String cardNumber, LocalDateTime when) {
        Optional<Employee> match = employeeRepo.findByRfidCardId(cardNumber);
        if (match.isEmpty() || !match.get().isActive()) {
            return ScanResult.rejectedUnknownCard(cardNumber);
        }
        Employee employee = match.get();

        LocalDate day = when.toLocalDate();
        List<AttendanceRecord> todayScans = attendanceRepo.findByEmployeeAndDate(employee, day);

        if (!todayScans.isEmpty()) {
            AttendanceRecord lastScan = todayScans.get(todayScans.size() - 1);
            long minutesSinceLast = ChronoUnit.MINUTES.between(lastScan.getScanDatetime(), when);
            if (minutesSinceLast < GUARD_MINUTES) {
                return ScanResult.ignoredTooSoon();
            }
        }

        ScanType direction = (todayScans.size() % 2 == 0) ? ScanType.ENTRY : ScanType.EXIT;

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setScanDatetime(when);
        record.setScanType(direction);
        record.setSyncedToCloud(false);
        attendanceRepo.save(record);

        return ScanResult.accepted(employee.getName(), employee.getEmployeeCode(), direction, when);
    }
}
