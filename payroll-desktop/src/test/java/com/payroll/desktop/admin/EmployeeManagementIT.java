package com.payroll.desktop.admin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.EmployeeNote;
import com.payroll.core.entity.OtEmployeeAuthorization;
import com.payroll.core.entity.ScanType;
import com.payroll.core.entity.StatutoryOverride;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.EmployeeNoteRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.ui.admin.EmployeeFormValidator;
import com.payroll.desktop.ui.admin.EmployeeManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeManagementIT {

    // ── uniqueness checks ──────────────────────────────────────────────────────

    @Test
    void duplicateEmployeeCodeIsRejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            repo.save(newEmployee("EMP-001", "RFID-001"));

            var result = new EmployeeFormValidator(repo).checkUniqueness("EMP-001", "RFID-999", null);

            assertThat(result.valid()).isFalse();
            assertThat(result.error()).contains("EMP-001");
        }
    }

    @Test
    void duplicateRfidIsRejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            repo.save(newEmployee("EMP-001", "RFID-001"));

            var result = new EmployeeFormValidator(repo).checkUniqueness("EMP-999", "RFID-001", null);

            assertThat(result.valid()).isFalse();
            assertThat(result.error()).contains("RFID-001");
        }
    }

    @Test
    void editingSameEmployeeDoesNotConflictWithOwnCode(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));

            // Editing EMP-001 with its own code/rfid — should pass (excludeId = emp.getId())
            var result = new EmployeeFormValidator(repo).checkUniqueness("EMP-001", "RFID-001", emp.getId());

            assertThat(result.valid()).isTrue();
        }
    }

    @Test
    void editShouldRejectCodeAlreadyUsedByDifferentEmployee(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            repo.save(newEmployee("EMP-001", "RFID-001"));
            Employee emp2 = repo.save(newEmployee("EMP-002", "RFID-002"));

            // emp2 tries to claim EMP-001's code
            var result = new EmployeeFormValidator(repo).checkUniqueness("EMP-001", "RFID-002", emp2.getId());

            assertThat(result.valid()).isFalse();
            assertThat(result.error()).contains("EMP-001");
        }
    }

    // ── deactivate (soft-delete) ───────────────────────────────────────────────

    @Test
    void deactivateSetsIsActiveFalseWithoutDeletingRecord(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));

            repo.deactivate(emp.getId());

            var found = repo.findById(emp.getId());
            assertThat(found).isPresent();
            assertThat(found.get().isActive()).isFalse();
            assertThat(found.get().getEmployeeCode()).isEqualTo("EMP-001");
        }
    }

    @Test
    void deactivatedEmployeeStillAppearInFindAll(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));
            repo.save(newEmployee("EMP-002", "RFID-002"));

            repo.deactivate(emp.getId());

            assertThat(repo.findAll()).hasSize(2);
            assertThat(repo.findAllActive()).hasSize(1)
                    .extracting(Employee::getEmployeeCode)
                    .containsExactly("EMP-002");
        }
    }

    // ── reactivate ───────────────────────────────────────────────────────────

    @Test
    void reactivateSetsIsActiveTrueAndWritesAuditEntry(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            var attendanceRepo = new AttendanceRecordRepository(db.getSessionFactory());
            var auditRepo = new AuditLogRepository(db.getSessionFactory());
            var service = new EmployeeManagementService(repo, attendanceRepo, auditRepo);

            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));
            repo.deactivate(emp.getId());
            emp = repo.findById(emp.getId()).orElseThrow();

            service.reactivate(emp, "super1");

            var found = repo.findById(emp.getId());
            assertThat(found).isPresent();
            assertThat(found.get().isActive()).isTrue();

            var auditEntries = auditRepo.findAll();
            assertThat(auditEntries).anySatisfy(entry -> {
                assertThat(entry.getAction()).isEqualTo("EMPLOYEE_REACTIVATED");
                assertThat(entry.getTargetRef()).isEqualTo("EMP-001");
                assertThat(entry.getUsername()).isEqualTo("super1");
            });
        }
    }

    // ── hard delete ──────────────────────────────────────────────────────────

    @Test
    void deleteRemovesEmployeeAndAllDependentRecords(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            var attendanceRepo = new AttendanceRecordRepository(db.getSessionFactory());
            var overrideRepo = new StatutoryOverrideRepository(db.getSessionFactory());
            var noteRepo = new EmployeeNoteRepository(db.getSessionFactory());
            var otAuthRepo = new OtEmployeeAuthorizationRepository(db.getSessionFactory());
            var auditRepo = new AuditLogRepository(db.getSessionFactory());
            var service = new EmployeeManagementService(repo, attendanceRepo, auditRepo);

            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));

            AttendanceRecord rec = new AttendanceRecord();
            rec.setEmployee(emp);
            rec.setScanDatetime(LocalDateTime.of(2026, 7, 1, 8, 0));
            rec.setScanType(ScanType.ENTRY);
            attendanceRepo.save(rec);

            overrideRepo.upsert(emp.getId(), "2026-07", new BigDecimal("20"), "manual review", "super1");

            EmployeeNote note = new EmployeeNote();
            note.setEmployeeId(emp.getId());
            note.setNoteDate(LocalDate.of(2026, 7, 1));
            note.setNoteText("late arrival");
            note.setCreatedBy("super1");
            noteRepo.save(note);

            otAuthRepo.upsert(emp.getId(), LocalDate.of(2026, 7, 1), true, "super1");

            service.deleteEmployee(emp, "super1");

            assertThat(repo.findById(emp.getId())).isEmpty();
            assertThat(attendanceRepo.countByEmployee(emp.getId())).isZero();
            assertThat(overrideRepo.findByEmployeeAndMonth(emp.getId(), "2026-07")).isEmpty();
            assertThat(noteRepo.findByEmployee(emp.getId())).isEmpty();
            assertThat(otAuthRepo.findByEmployeeAndDate(emp.getId(), LocalDate.of(2026, 7, 1))).isEmpty();
        }
    }

    @Test
    void deleteWritesAuditEntryBeforeDeletionAndItSurvives(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeRepository(db.getSessionFactory());
            var attendanceRepo = new AttendanceRecordRepository(db.getSessionFactory());
            var auditRepo = new AuditLogRepository(db.getSessionFactory());
            var service = new EmployeeManagementService(repo, attendanceRepo, auditRepo);

            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));

            AttendanceRecord rec = new AttendanceRecord();
            rec.setEmployee(emp);
            rec.setScanDatetime(LocalDateTime.of(2026, 7, 1, 8, 0));
            rec.setScanType(ScanType.ENTRY);
            attendanceRepo.save(rec);

            service.deleteEmployee(emp, "super1");

            assertThat(repo.findById(emp.getId())).isEmpty();

            var auditEntries = auditRepo.findAll();
            assertThat(auditEntries).anySatisfy(entry -> {
                assertThat(entry.getAction()).isEqualTo("EMPLOYEE_DELETED");
                assertThat(entry.getTargetRef()).contains("EMP-001").contains("Worker EMP-001");
                assertThat(entry.getOldValue()).isEqualTo("attendanceRecords=1");
                assertThat(entry.getUsername()).isEqualTo("super1");
            });
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static Employee newEmployee(String code, String rfid) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setName("Worker " + code);
        e.setRfidCardId(rfid);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1500.00"));
        return e;
    }
}
