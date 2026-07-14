package com.payroll.core.engine;

import com.payroll.core.constants.PayrollConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for all gross-salary and statutory-deduction math.
 * All monetary values use BigDecimal; RoundingMode.HALF_UP at 4 decimal places.
 */
public class GrossSalaryEngine {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Computes gross monthly salary from fractional daysWorked, floored at zero.
     *
     * absentDays = availableWorkingDays - daysWorked  (both BigDecimal, may be fractional)
     * gross = max(0, BASIC_SALARY - (PER_DAY_DEDUCTION × absentDays))
     */
    public BigDecimal grossSalary(int availableWorkingDays, BigDecimal daysWorked) {
        BigDecimal absentDays = BigDecimal.valueOf(availableWorkingDays).subtract(daysWorked);
        BigDecimal deduction = PayrollConstants.PER_DAY_DEDUCTION.multiply(absentDays);
        BigDecimal raw = PayrollConstants.BASIC_SALARY.subtract(deduction);
        return raw.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(SCALE, ROUNDING)
                : raw.setScale(SCALE, ROUNDING);
    }

    /** Convenience overload for whole-day counts. */
    public BigDecimal grossSalary(int availableWorkingDays, int daysWorked) {
        return grossSalary(availableWorkingDays, BigDecimal.valueOf(daysWorked));
    }

    public BigDecimal epfEmployeeDeduction(BigDecimal gross) {
        return gross.multiply(PayrollConstants.EPF_EMPLOYEE_RATE)
                .setScale(SCALE, ROUNDING);
    }

    public BigDecimal epfEmployerContribution(BigDecimal gross) {
        return gross.multiply(PayrollConstants.EPF_EMPLOYER_RATE)
                .setScale(SCALE, ROUNDING);
    }

    public BigDecimal etfContribution(BigDecimal gross) {
        return gross.multiply(PayrollConstants.ETF_RATE)
                .setScale(SCALE, ROUNDING);
    }

    /** Admin balance = gross salary minus employee EPF deduction. */
    public BigDecimal adminBalance(BigDecimal gross) {
        return gross.subtract(epfEmployeeDeduction(gross))
                .setScale(SCALE, ROUNDING);
    }
}
