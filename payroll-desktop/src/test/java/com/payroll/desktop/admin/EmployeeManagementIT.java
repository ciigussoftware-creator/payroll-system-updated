package com.payroll.desktop.admin;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.admin.EmployeeFormValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

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
