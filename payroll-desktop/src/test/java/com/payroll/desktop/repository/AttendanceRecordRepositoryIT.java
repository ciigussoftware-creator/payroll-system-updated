package com.payroll.desktop.repository;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.db.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceRecordRepositoryIT {

    private static Employee savedEmployee(EmployeeRepository empRepo, String code) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setName("Worker " + code);
        e.setRfidCardId("RFID-" + code);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1200.00"));
        return empRepo.save(e);
    }

    private static AttendanceRecord newRecord(Employee emp, LocalDateTime dt, ScanType type) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(emp);
        r.setScanDatetime(dt);
        r.setScanType(type);
        return r;
    }

    @Test
    void saveAndFindByIdRoundTrip(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());

            Employee emp = savedEmployee(empRepo, "EMP-001");
            AttendanceRecord saved = repo.save(
                    newRecord(emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY));

            assertThat(saved.getId()).isNotNull();
            Optional<AttendanceRecord> found = repo.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getScanType()).isEqualTo(ScanType.ENTRY);
            assertThat(found.get().getEmployee().getEmployeeCode()).isEqualTo("EMP-001");
        }
    }

    @Test
    void findByEmployeeAndDateReturnsCorrectScansInTimeOrder(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());

            Employee emp = savedEmployee(empRepo, "EMP-001");
            LocalDate targetDate = LocalDate.of(2026, 6, 20);

            // Saved in reverse order to prove ordering is by scanDatetime, not insert order
            repo.save(newRecord(emp, LocalDateTime.of(2026, 6, 20, 17, 0), ScanType.EXIT));
            repo.save(newRecord(emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY));
            // Different day — must not appear in results
            repo.save(newRecord(emp, LocalDateTime.of(2026, 6, 21, 8, 0), ScanType.ENTRY));

            List<AttendanceRecord> results = repo.findByEmployeeAndDate(emp, targetDate);
            assertThat(results).hasSize(2);
            assertThat(results.get(0).getScanType()).isEqualTo(ScanType.ENTRY);
            assertThat(results.get(1).getScanType()).isEqualTo(ScanType.EXIT);
        }
    }

    @Test
    void findUnsyncedReturnsOnlyUnsyncedRecords(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());

            Employee emp = savedEmployee(empRepo, "EMP-001");

            AttendanceRecord alreadySynced = newRecord(emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY);
            alreadySynced.setSyncedToCloud(true);
            alreadySynced.setCloudRecordId(999L);
            repo.save(alreadySynced);

            repo.save(newRecord(emp, LocalDateTime.of(2026, 6, 20, 17, 0), ScanType.EXIT));

            List<AttendanceRecord> unsynced = repo.findUnsynced();
            assertThat(unsynced).hasSize(1);
            assertThat(unsynced.get(0).getScanType()).isEqualTo(ScanType.EXIT);
        }
    }

    @Test
    void markSyncedUpdatesBothFieldsCorrectly(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());

            Employee emp = savedEmployee(empRepo, "EMP-001");
            AttendanceRecord saved = repo.save(
                    newRecord(emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY));

            repo.markSynced(saved.getId(), 42L);

            Optional<AttendanceRecord> found = repo.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().isSyncedToCloud()).isTrue();
            assertThat(found.get().getCloudRecordId()).isEqualTo(42L);
        }
    }

    @Test
    void findUnsyncedAfterMarkSyncedReturnsEmpty(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());

            Employee emp = savedEmployee(empRepo, "EMP-001");
            AttendanceRecord saved = repo.save(
                    newRecord(emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY));

            assertThat(repo.findUnsynced()).hasSize(1);

            repo.markSynced(saved.getId(), 100L);

            assertThat(repo.findUnsynced()).isEmpty();
        }
    }
}
