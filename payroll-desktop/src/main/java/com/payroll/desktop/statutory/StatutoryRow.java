package com.payroll.desktop.statutory;

import java.math.BigDecimal;
import java.util.Set;

public record StatutoryRow(
        Long employeeId,
        String employeeCode,
        String name,
        int availableWorkingDays,
        BigDecimal computedDaysWorked,
        BigDecimal effectiveDaysWorked,
        BigDecimal gross,
        BigDecimal epfEmployee,
        BigDecimal epfEmployer,
        BigDecimal etf,
        BigDecimal adminBalance,
        String overrideReason,
        Set<StatutoryFlag> flags
) {
    public boolean hasFlag(StatutoryFlag flag) {
        return flags.contains(flag);
    }
}
