package com.payroll.web.sync;

import com.payroll.core.entity.EmployeeCategory;

import java.math.BigDecimal;

public record EmployeeSyncRequest(
        String employeeCode,
        String name,
        String rfidCardId,
        EmployeeCategory category,
        BigDecimal grossDailySalary,
        BigDecimal epfEmployeeRate,
        BigDecimal epfEmployerRate,
        BigDecimal etfRate,
        boolean isActive
) {}
