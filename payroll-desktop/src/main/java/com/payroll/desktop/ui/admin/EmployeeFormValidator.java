package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.EmployeeCategory;
import com.payroll.desktop.repository.EmployeeRepository;

import java.math.BigDecimal;

public class EmployeeFormValidator {

    private final EmployeeRepository repository;

    public EmployeeFormValidator(EmployeeRepository repository) {
        this.repository = repository;
    }

    public record Result(boolean valid, String error) {
        public static Result ok()             { return new Result(true, null); }
        public static Result fail(String msg) { return new Result(false, msg); }
    }

    /**
     * Pure field validation — no DB access.
     * Safe to unit-test without a running database.
     */
    public static Result validateFields(String code, String name, String rfidCardId,
                                         EmployeeCategory category,
                                         String grossSalaryStr, String epfEmpStr,
                                         String epfErStr, String etfStr) {
        if (blank(code))        return Result.fail("Employee Code is required.");
        if (blank(name))        return Result.fail("Name is required.");
        if (blank(rfidCardId))  return Result.fail("RFID Card ID is required.");
        if (category == null)   return Result.fail("Category is required.");
        if (!positiveDecimal(grossSalaryStr))
            return Result.fail("Gross Daily Salary must be a positive number.");
        if (!nonNegativeDecimal(epfEmpStr))
            return Result.fail("EPF employee rate must be a non-negative number.");
        if (!nonNegativeDecimal(epfErStr))
            return Result.fail("EPF employer rate must be a non-negative number.");
        if (!nonNegativeDecimal(etfStr))
            return Result.fail("ETF rate must be a non-negative number.");
        return Result.ok();
    }

    /**
     * Uniqueness check against the live database.
     * Pass excludeId = null for new employees; pass the existing employee's id when editing
     * so the record doesn't conflict with itself.
     */
    public Result checkUniqueness(String code, String rfidCardId, Long excludeId) {
        var byCode = repository.findByEmployeeCode(code.trim());
        if (byCode.isPresent() && !byCode.get().getId().equals(excludeId))
            return Result.fail("Employee Code '" + code.trim() + "' is already in use.");

        var byRfid = repository.findByRfidCardId(rfidCardId.trim());
        if (byRfid.isPresent() && !byRfid.get().getId().equals(excludeId))
            return Result.fail("RFID Card ID '" + rfidCardId.trim() + "' is already assigned to another employee.");

        return Result.ok();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static boolean positiveDecimal(String s) {
        if (blank(s)) return false;
        try { return new BigDecimal(s.trim()).compareTo(BigDecimal.ZERO) > 0; }
        catch (NumberFormatException e) { return false; }
    }

    private static boolean nonNegativeDecimal(String s) {
        if (blank(s)) return false;
        try { return new BigDecimal(s.trim()).compareTo(BigDecimal.ZERO) >= 0; }
        catch (NumberFormatException e) { return false; }
    }
}
