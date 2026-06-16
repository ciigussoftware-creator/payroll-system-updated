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
     * Computes gross monthly salary.
     *
     * absentDays = availableWorkingDays - daysWorked
     * gross = BASIC_SALARY - (PER_DAY_DEDUCTION * absentDays)
     *
     * TODO: confirm whether gross should floor at zero when absences exceed
     *       the salary. Currently returns the raw (possibly negative) result.
     *       Example: 23 absent days → 23 * 1200 = 27600 deducted → gross = 2400
     *       (which is fine), but theoretically more absent days than the divisor
     *       allows could yield a negative. Awaiting business rule confirmation.
     */
    public BigDecimal grossSalary(int availableWorkingDays, int daysWorked) {
        int absentDays = availableWorkingDays - daysWorked;
        BigDecimal deduction = PayrollConstants.PER_DAY_DEDUCTION
                .multiply(BigDecimal.valueOf(absentDays));
        return PayrollConstants.BASIC_SALARY.subtract(deduction)
                .setScale(SCALE, ROUNDING);
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
