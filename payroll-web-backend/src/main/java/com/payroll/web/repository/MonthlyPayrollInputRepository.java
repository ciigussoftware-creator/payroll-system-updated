package com.payroll.web.repository;

import com.payroll.core.entity.MonthlyPayrollInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyPayrollInputRepository extends JpaRepository<MonthlyPayrollInput, Long> {
    Optional<MonthlyPayrollInput> findByCompanyIdAndEmployeeCodeAndPeriodMonth(
            Long companyId, String employeeCode, String periodMonth);

    List<MonthlyPayrollInput> findByCompanyIdAndPeriodMonth(Long companyId, String periodMonth);
}
