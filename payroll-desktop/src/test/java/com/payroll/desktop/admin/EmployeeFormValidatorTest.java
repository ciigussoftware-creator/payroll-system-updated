package com.payroll.desktop.admin;

import com.payroll.core.entity.EmployeeCategory;
import com.payroll.desktop.ui.admin.EmployeeFormValidator;
import com.payroll.desktop.ui.admin.EmployeeFormValidator.Result;
import org.junit.jupiter.api.Test;

import static com.payroll.core.entity.EmployeeCategory.STANDARD;
import static org.assertj.core.api.Assertions.assertThat;

class EmployeeFormValidatorTest {

    // ── required-field validation ──────────────────────────────────────────────

    @Test
    void emptyCodeIsRejected() {
        assertFail(v("", "Name", "RFID-1", STANDARD, "1500", "0.08", "0.12", "0.03"), "Code");
    }

    @Test
    void blankCodeIsRejected() {
        assertFail(v("   ", "Name", "RFID-1", STANDARD, "1500", "0.08", "0.12", "0.03"), "Code");
    }

    @Test
    void emptyNameIsRejected() {
        assertFail(v("EMP001", "", "RFID-1", STANDARD, "1500", "0.08", "0.12", "0.03"), "Name");
    }

    @Test
    void emptyRfidIsRejected() {
        assertFail(v("EMP001", "Name", "", STANDARD, "1500", "0.08", "0.12", "0.03"), "RFID");
    }

    @Test
    void nullCategoryIsRejected() {
        assertFail(v("EMP001", "Name", "RFID-1", null, "1500", "0.08", "0.12", "0.03"), "Category");
    }

    // ── numeric validation ─────────────────────────────────────────────────────

    @Test
    void negativeSalaryIsRejected() {
        assertFail(v("EMP001", "Name", "RFID-1", STANDARD, "-1", "0.08", "0.12", "0.03"), "Salary");
    }

    @Test
    void zeroSalaryIsRejected() {
        assertFail(v("EMP001", "Name", "RFID-1", STANDARD, "0", "0.08", "0.12", "0.03"), "Salary");
    }

    @Test
    void nonNumericSalaryIsRejected() {
        assertFail(v("EMP001", "Name", "RFID-1", STANDARD, "abc", "0.08", "0.12", "0.03"), "Salary");
    }

    @Test
    void emptySalaryIsRejected() {
        assertFail(v("EMP001", "Name", "RFID-1", STANDARD, "", "0.08", "0.12", "0.03"), "Salary");
    }

    @Test
    void negativeEpfRateIsRejected() {
        assertFail(v("EMP001", "Name", "RFID-1", STANDARD, "1500", "-0.01", "0.12", "0.03"), "EPF employee");
    }

    @Test
    void zeroRateIsAllowedForEpf() {
        assertOk(v("EMP001", "Name", "RFID-1", STANDARD, "1500", "0", "0", "0"));
    }

    @Test
    void validDataPassesValidation() {
        assertOk(v("EMP001", "Worker One", "RFID-001", STANDARD, "1500.00", "0.08", "0.12", "0.03"));
    }

    @Test
    void peelingCategoryIsAccepted() {
        assertOk(v("EMP002", "Worker Two", "RFID-002", EmployeeCategory.PEELING, "2000", "0.08", "0.12", "0.03"));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Result v(String code, String name, String rfid, EmployeeCategory cat,
                             String salary, String epfEmp, String epfEr, String etf) {
        return EmployeeFormValidator.validateFields(code, name, rfid, cat, salary, epfEmp, epfEr, etf);
    }

    private static void assertOk(Result r) {
        assertThat(r.valid()).as(r.error()).isTrue();
    }

    private static void assertFail(Result r, String errorContains) {
        assertThat(r.valid()).isFalse();
        assertThat(r.error()).containsIgnoringCase(errorContains);
    }
}
