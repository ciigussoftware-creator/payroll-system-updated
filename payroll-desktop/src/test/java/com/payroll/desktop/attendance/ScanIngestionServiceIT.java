package com.payroll.desktop.attendance;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanIngestionServiceIT {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 6, 26, 8, 0);

    // ── happy path: alternating direction ────────────────────────────────────────

    @Test
    void firstTapIsAcceptedAsEntry(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            repos.saveEmployee("EMP-001", "CARD-001", true);

            ScanResult result = repos.service().recordScan("CARD-001", BASE);

            assertThat(result.outcome()).isEqualTo(ScanResult.Outcome.ACCEPTED);
            assertThat(result.scanType()).isEqualTo(ScanType.ENTRY);
            assertThat(result.employeeCode()).isEqualTo("EMP-001");
        }
    }

    @Test
    void secondTapAfter30MinIsAcceptedAsExit(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            repos.saveEmployee("EMP-001", "CARD-001", true);
            var svc = repos.service();

            svc.recordScan("CARD-001", BASE);
            ScanResult result = svc.recordScan("CARD-001", BASE.plusMinutes(31));

            assertThat(result.outcome()).isEqualTo(ScanResult.Outcome.ACCEPTED);
            assertThat(result.scanType()).isEqualTo(ScanType.EXIT);
        }
    }

    @Test
    void thirdTapAfter30MinIsAcceptedAsEntry(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            repos.saveEmployee("EMP-001", "CARD-001", true);
            var svc = repos.service();

            svc.recordScan("CARD-001", BASE);
            svc.recordScan("CARD-001", BASE.plusMinutes(31));
            ScanResult result = svc.recordScan("CARD-001", BASE.plusMinutes(62));

            assertThat(result.outcome()).isEqualTo(ScanResult.Outcome.ACCEPTED);
            assertThat(result.scanType()).isEqualTo(ScanType.ENTRY);
        }
    }

    // ── guard: duplicate tap within 30 minutes ────────────────────────────────────

    @Test
    void doubleTapWithin30MinIsIgnoredAndNotSaved(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-001", "CARD-001", true);
            var svc = repos.service();

            svc.recordScan("CARD-001", BASE);
            ScanResult result = svc.recordScan("CARD-001", BASE.plusMinutes(10));

            assertThat(result.outcome()).isEqualTo(ScanResult.Outcome.IGNORED_TOO_SOON);
            List<?> scans = repos.attendance.findByEmployeeAndDate(emp, BASE.toLocalDate());
            assertThat(scans).hasSize(1);
        }
    }

    // ── rejection ─────────────────────────────────────────────────────────────────

    @Test
    void unknownCardIsRejectedAndNotSaved(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);

            ScanResult result = repos.service().recordScan("NO-SUCH-CARD", BASE);

            assertThat(result.outcome()).isEqualTo(ScanResult.Outcome.REJECTED_UNKNOWN_CARD);
            assertThat(result.cardNumber()).isEqualTo("NO-SUCH-CARD");
        }
    }

    @Test
    void inactiveEmployeeCardIsRejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            repos.saveEmployee("EMP-999", "CARD-999", false);

            ScanResult result = repos.service().recordScan("CARD-999", BASE);

            assertThat(result.outcome()).isEqualTo(ScanResult.Outcome.REJECTED_UNKNOWN_CARD);
        }
    }

    // ── currently-in logic ────────────────────────────────────────────────────────

    @Test
    void oddScanCountMeansCurrentlyIn(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-001", "CARD-001", true);
            var svc = repos.service();

            // 1 scan → in (ENTRY)
            svc.recordScan("CARD-001", BASE);
            var scans = repos.attendance.findByEmployeeAndDate(emp, BASE.toLocalDate());
            assertThat(scans).hasSize(1);
            assertThat(scans.get(0).getScanType()).isEqualTo(ScanType.ENTRY);

            // 2 scans → out (EXIT)
            svc.recordScan("CARD-001", BASE.plusMinutes(31));
            scans = repos.attendance.findByEmployeeAndDate(emp, BASE.toLocalDate());
            assertThat(scans).hasSize(2);
            assertThat(scans.get(1).getScanType()).isEqualTo(ScanType.EXIT);

            // 3 scans → in again (ENTRY)
            svc.recordScan("CARD-001", BASE.plusMinutes(62));
            scans = repos.attendance.findByEmployeeAndDate(emp, BASE.toLocalDate());
            assertThat(scans).hasSize(3);
            assertThat(scans.get(2).getScanType()).isEqualTo(ScanType.ENTRY);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static class Repos {
        final AttendanceRecordRepository attendance;
        final EmployeeRepository employees;

        Repos(DatabaseManager db) {
            this.attendance = new AttendanceRecordRepository(db.getSessionFactory());
            this.employees  = new EmployeeRepository(db.getSessionFactory());
        }

        ScanIngestionService service() {
            return new ScanIngestionService(attendance, employees);
        }

        Employee saveEmployee(String code, String rfid, boolean active) {
            Employee e = new Employee();
            e.setEmployeeCode(code);
            e.setName("Worker " + code);
            e.setRfidCardId(rfid);
            e.setCategory(EmployeeCategory.STANDARD);
            e.setGrossDailySalary(new BigDecimal("1500.00"));
            e.setActive(active);
            return employees.save(e);
        }
    }
}
