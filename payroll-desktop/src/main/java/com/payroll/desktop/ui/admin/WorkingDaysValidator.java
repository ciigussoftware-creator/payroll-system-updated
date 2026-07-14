package com.payroll.desktop.ui.admin;

public class WorkingDaysValidator {

    private WorkingDaysValidator() {}

    public record Result(boolean valid, String error) {
        public static Result ok()             { return new Result(true, null); }
        public static Result fail(String msg) { return new Result(false, msg); }
    }

    /** Validates that input is a whole number between 1 and 31 inclusive. */
    public static Result validate(String input) {
        if (input == null || input.isBlank())
            return Result.fail("Working days is required.");
        try {
            int days = Integer.parseInt(input.trim());
            if (days < 1)  return Result.fail("Working days must be at least 1.");
            if (days > 31) return Result.fail("Working days cannot exceed 31.");
            return Result.ok();
        } catch (NumberFormatException e) {
            return Result.fail("Working days must be a whole number (e.g. 23).");
        }
    }

    /** Parses input that has already been validated. */
    public static int parse(String input) {
        return Integer.parseInt(input.trim());
    }
}
