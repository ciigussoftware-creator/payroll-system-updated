package com.payroll.desktop.db;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseManagerIT {

    @Test
    void saveAndReadEmployeeRoundTrips(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {

            Employee emp = new Employee();
            emp.setEmployeeCode("EMP-001");
            emp.setName("Vijitha Bandara");
            emp.setRfidCardId("46");
            emp.setCategory(EmployeeCategory.STANDARD);
            emp.setGrossDailySalary(new BigDecimal("1500.00"));
            emp.setActive(true);

            try (Session session = db.getSessionFactory().openSession()) {
                session.beginTransaction();
                session.persist(emp);
                session.getTransaction().commit();
            }

            try (Session session = db.getSessionFactory().openSession()) {
                Employee found = session.createQuery(
                        "FROM Employee WHERE employeeCode = :code", Employee.class)
                        .setParameter("code", "EMP-001")
                        .uniqueResult();

                assertThat(found).isNotNull();
                assertThat(found.getEmployeeCode()).isEqualTo("EMP-001");
                assertThat(found.getName()).isEqualTo("Vijitha Bandara");
                assertThat(found.getRfidCardId()).isEqualTo("46");
                assertThat(found.getCategory()).isEqualTo(EmployeeCategory.STANDARD);
                assertThat(found.getGrossDailySalary()).isEqualByComparingTo(new BigDecimal("1500.00"));
                assertThat(found.getEpfEmployeeRate()).isEqualByComparingTo(new BigDecimal("0.08"));
                assertThat(found.getEpfEmployerRate()).isEqualByComparingTo(new BigDecimal("0.12"));
                assertThat(found.getEtfRate()).isEqualByComparingTo(new BigDecimal("0.03"));
                assertThat(found.isActive()).isTrue();
                assertThat(found.getCreatedAt()).isNotNull();
            }
        }

        Path dbFile = tempDir.resolve("payroll.mv.db");
        assertThat(dbFile).exists();
        assertThat(Files.size(dbFile)).isGreaterThan(0);
    }

    @Test
    void wrongPasswordThrowsWhenOpeningEncryptedFile(@TempDir Path tempDir) throws IOException {
        // create a valid encrypted DB so the file exists on disk
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            // open and immediately close to materialise the encrypted file
        }

        // opening the same file with the wrong key must throw — proves CIPHER=AES is active
        assertThatThrownBy(() -> new DatabaseManager(tempDir, "DEFINITELY_WRONG_KEY"))
                .isInstanceOf(Exception.class);
    }
}
