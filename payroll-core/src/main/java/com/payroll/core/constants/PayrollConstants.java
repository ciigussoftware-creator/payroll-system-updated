package com.payroll.core.constants;

import java.math.BigDecimal;

public final class PayrollConstants {

    private PayrollConstants() {}

    public static final BigDecimal BASIC_SALARY       = new BigDecimal("30000");
    public static final BigDecimal SALARY_DIVISOR     = new BigDecimal("25");
    public static final BigDecimal PER_DAY_DEDUCTION  = new BigDecimal("1200");   // 30000 / 25
    public static final BigDecimal OT_HOURLY_CONSTANT = new BigDecimal("200");    // 25 * 8
    public static final BigDecimal OT_MULTIPLIER      = new BigDecimal("1.5");
    public static final BigDecimal OT_RATE_PER_HOUR   = new BigDecimal("225");    // (30000 / 200) * 1.5

    public static final BigDecimal EPF_EMPLOYEE_RATE  = new BigDecimal("0.08");
    public static final BigDecimal EPF_EMPLOYER_RATE  = new BigDecimal("0.12");
    public static final BigDecimal ETF_RATE           = new BigDecimal("0.03");
}
