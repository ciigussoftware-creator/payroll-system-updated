package com.payroll.desktop.repository;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.desktop.db.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRepositoryIT {

    private static Employee newEmployee(String code, String rfid) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setName("Worker " + code);
        e.setRfidCardId(rfid);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1500.00"));
        return e;
    }

    @Test
    void saveAndFindByIdRoundTrip(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository repo = new EmployeeRepository(db.getSessionFactory());

            Employee saved = repo.save(newEmployee("EMP-001", "RFID-001"));

            assertThat(saved.getId()).isNotNull();
            Optional<Employee> found = repo.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getEmployeeCode()).isEqualTo("EMP-001");
            assertThat(found.get().getName()).isEqualTo("Worker EMP-001");
            assertThat(found.get().getGrossDailySalary()).isEqualByComparingTo("1500.00");
        }
    }

    @Test
    void findByEmployeeCodeReturnsCorrectEmployee(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository repo = new EmployeeRepository(db.getSessionFactory());

            repo.save(newEmployee("EMP-001", "RFID-001"));
            repo.save(newEmployee("EMP-002", "RFID-002"));

            Optional<Employee> found = repo.findByEmployeeCode("EMP-002");
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Worker EMP-002");
        }
    }

    @Test
    void findByRfidCardIdReturnsCorrectEmployee(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository repo = new EmployeeRepository(db.getSessionFactory());

            repo.save(newEmployee("EMP-001", "RFID-A1"));
            repo.save(newEmployee("EMP-002", "RFID-B2"));

            Optional<Employee> found = repo.findByRfidCardId("RFID-B2");
            assertThat(found).isPresent();
            assertThat(found.get().getEmployeeCode()).isEqualTo("EMP-002");
        }
    }

    @Test
    void findAllActiveExcludesDeactivatedEmployees(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository repo = new EmployeeRepository(db.getSessionFactory());

            repo.save(newEmployee("EMP-001", "RFID-001"));
            Employee emp2 = repo.save(newEmployee("EMP-002", "RFID-002"));
            repo.save(newEmployee("EMP-003", "RFID-003"));

            repo.deactivate(emp2.getId());

            List<Employee> active = repo.findAllActive();
            assertThat(active).hasSize(2);
            assertThat(active).extracting(Employee::getEmployeeCode)
                    .containsExactlyInAnyOrder("EMP-001", "EMP-003");
        }
    }

    @Test
    void deactivateSetsIsActiveFalseAndFindAllActiveExcludesIt(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository repo = new EmployeeRepository(db.getSessionFactory());

            Employee emp = repo.save(newEmployee("EMP-001", "RFID-001"));

            repo.deactivate(emp.getId());

            Optional<Employee> found = repo.findById(emp.getId());
            assertThat(found).isPresent();
            assertThat(found.get().isActive()).isFalse();

            assertThat(repo.findAllActive()).isEmpty();
        }
    }
}
