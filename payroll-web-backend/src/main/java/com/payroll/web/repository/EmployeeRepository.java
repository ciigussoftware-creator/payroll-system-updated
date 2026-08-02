package com.payroll.web.repository;

import com.payroll.core.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeCodeAndCompanyId(String employeeCode, Long companyId);
}
