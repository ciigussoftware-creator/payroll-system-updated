package com.payroll.core.attendance;

import java.math.BigDecimal;
import java.util.Set;

public class DayResult {

    private final DayClassification dayClassification;
    private final BigDecimal dayCredit;
    private final int otMinutes;
    private final BigDecimal otHoursDecimal;
    private final Set<DayFlag> flags;

    public DayResult(
            DayClassification dayClassification,
            BigDecimal dayCredit,
            int otMinutes,
            BigDecimal otHoursDecimal,
            Set<DayFlag> flags) {
        this.dayClassification = dayClassification;
        this.dayCredit = dayCredit;
        this.otMinutes = otMinutes;
        this.otHoursDecimal = otHoursDecimal;
        this.flags = flags;
    }

    public DayClassification getDayClassification() { return dayClassification; }
    public BigDecimal getDayCredit() { return dayCredit; }
    public int getOtMinutes() { return otMinutes; }
    public BigDecimal getOtHoursDecimal() { return otHoursDecimal; }
    public Set<DayFlag> getFlags() { return flags; }
}
