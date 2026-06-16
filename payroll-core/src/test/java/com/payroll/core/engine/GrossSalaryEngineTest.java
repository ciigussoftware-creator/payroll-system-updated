package com.payroll.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GrossSalaryEngineTest {

    private GrossSalaryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GrossSalaryEngine();
    }

    // -------------------------------------------------------------------------
    // GOLDEN TEST — Vijitha Bandara, EPF 46, April 2026
    // availableWorkingDays=23, daysWorked=18 → absentDays=5
    // deduction = 1200 * 5 = 6000
    // grossSalary = 30000 - 6000 = 24000
    // epfEmployee (8%) = 1920
    // adminBalance = 24000 - 1920 = 22080
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Golden: Vijitha Bandara April-2026 — 18/23 days worked")
    void goldenTest_VijithaBandara_April2026() {
        BigDecimal gross = engine.grossSalary(23, 18);
        BigDecimal epfEmployee = engine.epfEmployeeDeduction(gross);
        BigDecimal adminBal = engine.adminBalance(gross);

        assertThat(gross.compareTo(new BigDecimal("24000"))).isZero();
        assertThat(epfEmployee.compareTo(new BigDecimal("1920"))).isZero();
        assertThat(adminBal.compareTo(new BigDecimal("22080"))).isZero();

        // deduction cross-check (1200 * 5 = 6000 subtracted from 30000)
        BigDecimal expectedDeduction = new BigDecimal("6000");
        assertThat(new BigDecimal("30000").subtract(gross).compareTo(expectedDeduction)).isZero();
    }

    // -------------------------------------------------------------------------
    // Zero absences: all 23 days worked
    // gross = 30000 - 0 = 30000
    // epfEmployee = 30000 * 0.08 = 2400
    // adminBalance = 30000 - 2400 = 27600
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Full attendance: 23/23 days worked → gross 30000")
    void fullAttendance_23of23() {
        BigDecimal gross = engine.grossSalary(23, 23);
        BigDecimal epfEmployee = engine.epfEmployeeDeduction(gross);
        BigDecimal adminBal = engine.adminBalance(gross);

        assertThat(gross.compareTo(new BigDecimal("30000"))).isZero();
        assertThat(epfEmployee.compareTo(new BigDecimal("2400"))).isZero();
        assertThat(adminBal.compareTo(new BigDecimal("27600"))).isZero();
    }

    // -------------------------------------------------------------------------
    // All absent: 0 days worked out of 23
    // absentDays = 23, deduction = 23 * 1200 = 27600
    // gross = 30000 - 27600 = 2400
    //
    // TODO: confirm floor-at-zero rule. Currently computes raw value.
    // If an employee has more absent days than SALARY_DIVISOR allows
    // (e.g., 26 absent on a 25-divisor scale) gross would go negative.
    // Awaiting business rule: should gross floor at 0?
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("All absent: 0/23 days worked → gross 2400 (raw, no floor)")
    void allAbsent_0of23() {
        BigDecimal gross = engine.grossSalary(23, 0);
        BigDecimal epfEmployee = engine.epfEmployeeDeduction(gross);
        BigDecimal adminBal = engine.adminBalance(gross);

        assertThat(gross.compareTo(new BigDecimal("2400"))).isZero();
        assertThat(epfEmployee.compareTo(new BigDecimal("192"))).isZero();
        assertThat(adminBal.compareTo(new BigDecimal("2208"))).isZero();
    }

    // -------------------------------------------------------------------------
    // Typical mid-month: 20/23 days worked (3 absent)
    // gross = 30000 - 3600 = 26400
    // epfEmployee = 26400 * 0.08 = 2112
    // epfEmployer = 26400 * 0.12 = 3168
    // etf = 26400 * 0.03 = 792
    // adminBalance = 26400 - 2112 = 24288
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Typical: 20/23 days worked → gross 26400, all statutory figures")
    void typical_20of23_allStatutory() {
        BigDecimal gross = engine.grossSalary(23, 20);

        assertThat(gross.compareTo(new BigDecimal("26400"))).isZero();
        assertThat(engine.epfEmployeeDeduction(gross).compareTo(new BigDecimal("2112"))).isZero();
        assertThat(engine.epfEmployerContribution(gross).compareTo(new BigDecimal("3168"))).isZero();
        assertThat(engine.etfContribution(gross).compareTo(new BigDecimal("792"))).isZero();
        assertThat(engine.adminBalance(gross).compareTo(new BigDecimal("24288"))).isZero();
    }

    // -------------------------------------------------------------------------
    // Shorter month: 22 available days, 22 worked → full basic
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Short month: 22/22 days worked → gross still 30000")
    void shortMonth_22of22() {
        BigDecimal gross = engine.grossSalary(22, 22);
        assertThat(gross.compareTo(new BigDecimal("30000"))).isZero();
    }
}
