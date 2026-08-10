package com.payroll.web.payrollinput;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record MonthlyPayrollInputRequest(
        @DecimalMin(value = "0", message = "must not be negative") BigDecimal allowance,
        @DecimalMin(value = "0", message = "must not be negative") BigDecimal salaryAdvance,
        @DecimalMin(value = "0", message = "must not be negative") BigDecimal festivalAdvance
) {}
