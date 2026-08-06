package com.payroll.web.payrollinput;

import com.payroll.core.entity.MonthlyPayrollInput;

import java.math.BigDecimal;
import java.time.Instant;

public record MonthlyPayrollInputResponse(
        String employeeCode,
        String periodMonth,
        BigDecimal allowance,
        BigDecimal salaryAdvance,
        BigDecimal festivalAdvance,
        String updatedBy,
        Instant updatedAt
) {
    static MonthlyPayrollInputResponse from(MonthlyPayrollInput input) {
        return new MonthlyPayrollInputResponse(
                input.getEmployeeCode(),
                input.getPeriodMonth(),
                input.getAllowance(),
                input.getSalaryAdvance(),
                input.getFestivalAdvance(),
                input.getUpdatedBy(),
                input.getUpdatedAt());
    }
}
