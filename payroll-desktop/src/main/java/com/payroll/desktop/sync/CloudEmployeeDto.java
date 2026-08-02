package com.payroll.desktop.sync;

import com.payroll.core.entity.EmployeeCategory;

import java.math.BigDecimal;

/** Wire-format mirror of payroll-web-backend's EmployeeSyncRequest. */
record CloudEmployeeDto(
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
