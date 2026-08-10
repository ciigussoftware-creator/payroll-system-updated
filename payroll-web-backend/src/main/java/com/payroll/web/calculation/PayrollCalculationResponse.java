package com.payroll.web.calculation;

import java.math.BigDecimal;

/**
 * Per-employee confidential payroll calculation for a given month. When
 * {@code workingDaysNotSet} is true, no money fields (otPay, gross, epfEmployee,
 * epfEmployer, etf, allowance, salaryAdvance, festivalAdvance, takeHome) are populated —
 * the same spirit as the desktop's Statutory Export screen for an unconfigured month.
 */
public record PayrollCalculationResponse(
        String employeeCode,
        String name,
        int availableWorkingDays,
        BigDecimal daysWorked,
        BigDecimal otHours,
        BigDecimal otPay,
        BigDecimal gross,
        BigDecimal epfEmployee,
        BigDecimal epfEmployer,
        BigDecimal etf,
        BigDecimal allowance,
        BigDecimal salaryAdvance,
        BigDecimal festivalAdvance,
        BigDecimal takeHome,
        boolean workingDaysNotSet
) {}
