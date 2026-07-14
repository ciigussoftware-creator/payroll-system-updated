package com.payroll.desktop.admin;

import com.payroll.desktop.ui.admin.WorkingDaysValidator;
import com.payroll.desktop.ui.admin.WorkingDaysValidator.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingDaysValidatorTest {

    // ── valid inputs ───────────────────────────────────────────────────────────

    @Test
    void twentyThreeIsValid() {
        assertOk(WorkingDaysValidator.validate("23"));
    }

    @Test
    void boundaryOneLowerIsValid() {
        assertOk(WorkingDaysValidator.validate("1"));
    }

    @Test
    void boundaryThirtyOneUpperIsValid() {
        assertOk(WorkingDaysValidator.validate("31"));
    }

    @Test
    void inputWithWhitespaceIsValid() {
        assertOk(WorkingDaysValidator.validate("  22  "));
    }

    // ── invalid inputs ─────────────────────────────────────────────────────────

    @Test
    void zeroIsInvalid() {
        assertFail(WorkingDaysValidator.validate("0"), "1");
    }

    @Test
    void thirtyTwoIsInvalid() {
        assertFail(WorkingDaysValidator.validate("32"), "31");
    }

    @Test
    void negativeIsInvalid() {
        assertFail(WorkingDaysValidator.validate("-1"), "1");
    }

    @Test
    void emptyStringIsInvalid() {
        assertFail(WorkingDaysValidator.validate(""), "required");
    }

    @Test
    void blankStringIsInvalid() {
        assertFail(WorkingDaysValidator.validate("   "), "required");
    }

    @Test
    void nullIsInvalid() {
        assertFail(WorkingDaysValidator.validate(null), "required");
    }

    @Test
    void nonNumericIsInvalid() {
        assertFail(WorkingDaysValidator.validate("abc"), "whole number");
    }

    @Test
    void decimalIsInvalid() {
        assertFail(WorkingDaysValidator.validate("22.5"), "whole number");
    }

    // ── parse (post-validation) ───────────────────────────────────────────────

    @Test
    void parseExtractsInt() {
        assertThat(WorkingDaysValidator.parse("23")).isEqualTo(23);
        assertThat(WorkingDaysValidator.parse("  5  ")).isEqualTo(5);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static void assertOk(Result r) {
        assertThat(r.valid()).as(r.error()).isTrue();
    }

    private static void assertFail(Result r, String errorContains) {
        assertThat(r.valid()).isFalse();
        assertThat(r.error()).containsIgnoringCase(errorContains);
    }
}
