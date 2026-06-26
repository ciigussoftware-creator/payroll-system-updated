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
    // gross = 30000 - 27600 = 2400  (still positive, floor does not trigger)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("All absent: 0/23 days worked → gross 2400")
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

    // -------------------------------------------------------------------------
    // Floor test: availableWorkingDays=26, daysWorked=0
    // raw = 30000 - (1200 * 26) = 30000 - 31200 = -1200  → floored to 0
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Floor: 0/26 days worked → raw -1200 floored to Rs. 0.00")
    void grossFloor_26available_0worked() {
        BigDecimal gross = engine.grossSalary(26, 0);
        assertThat(gross.compareTo(BigDecimal.ZERO)).isZero();
    }

    // =========================================================================
    // Fractional daysWorked (BigDecimal overload)
    // =========================================================================

    // Half-day: 22 available, 0.5 worked
    // absentDays = 21.5 → deduction = 1200 × 21.5 = 25,800 → gross = 4,200
    // EPF8% = 336, EPF12% = 504, ETF3% = 126, adminBalance = 3,864
    @Test
    @DisplayName("Half-day: 22 available, 0.5 worked → gross 4200")
    void halfDay_22available_0point5worked() {
        BigDecimal gross = engine.grossSalary(22, new BigDecimal("0.5"));

        assertThat(gross).isEqualByComparingTo("4200");
        assertThat(engine.epfEmployeeDeduction(gross)).isEqualByComparingTo("336");
        assertThat(engine.epfEmployerContribution(gross)).isEqualByComparingTo("504");
        assertThat(engine.etfContribution(gross)).isEqualByComparingTo("126");
        assertThat(engine.adminBalance(gross)).isEqualByComparingTo("3864");
    }

    // Zero days (BigDecimal path): 22 available, 0 worked
    // absentDays = 22 → deduction = 26,400 → gross = 3,600
    @Test
    @DisplayName("Zero days (BD): 22 available, 0 worked → gross 3600")
    void zeroDaysBigDecimal_22available() {
        BigDecimal gross = engine.grossSalary(22, new BigDecimal("0"));
        assertThat(gross).isEqualByComparingTo("3600");
    }

    // Fractional mix: 23 available, 18.5 worked
    // absentDays = 4.5 → deduction = 5,400 → gross = 24,600
    @Test
    @DisplayName("Fractional mix: 23 available, 18.5 worked → gross 24600")
    void fractionalMix_23available_18point5worked() {
        BigDecimal gross = engine.grossSalary(23, new BigDecimal("18.5"));
        assertThat(gross).isEqualByComparingTo("24600");
    }

    // Golden stays correct via int overload delegating to BigDecimal overload
    @Test
    @DisplayName("Golden (BD): 23 available, 18 worked → gross 24000, EPF8% 1920, balance 22080")
    void goldenBigDecimal_23available_18worked() {
        BigDecimal gross = engine.grossSalary(23, new BigDecimal("18"));
        assertThat(gross).isEqualByComparingTo("24000");
        assertThat(engine.epfEmployeeDeduction(gross)).isEqualByComparingTo("1920");
        assertThat(engine.adminBalance(gross)).isEqualByComparingTo("22080");
    }

    // Floor (BigDecimal path): 0/26 → negative → floored to 0
    @Test
    @DisplayName("Floor (BD): 0/26 days → floored to 0")
    void floorBigDecimal_26available_0worked() {
        BigDecimal gross = engine.grossSalary(26, new BigDecimal("0"));
        assertThat(gross).isEqualByComparingTo("0");
    }
}
