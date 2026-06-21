package com.payroll.core.attendance;

import com.payroll.core.entity.EmployeeCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OtRulesTest {

    // -------------------------------------------------------------------------
    // isOtAuthorized — per-employee gate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("gate: switches set today, this employee TRUE → authorized")
    void gate_switchesSetToday_employeeTrue_authorized() {
        DayInput input = input(true, Boolean.TRUE);
        assertThat(OtRules.isOtAuthorized(input)).isTrue();
    }

    @Test
    @DisplayName("gate: switches set today, this employee FALSE → NOT authorized")
    void gate_switchesSetToday_employeeFalse_notAuthorized() {
        DayInput input = input(true, Boolean.FALSE);
        assertThat(OtRules.isOtAuthorized(input)).isFalse();
    }

    @Test
    @DisplayName("gate: switches set today, this employee null → NOT authorized")
    void gate_switchesSetToday_employeeNull_notAuthorized() {
        DayInput input = input(true, null);
        assertThat(OtRules.isOtAuthorized(input)).isFalse();
    }

    @Test
    @DisplayName("gate: no switches set today → authorized regardless of per-employee flag")
    void gate_noSwitchesSetToday_alwaysAuthorized() {
        DayInput input = input(false, null);
        assertThat(OtRules.isOtAuthorized(input)).isTrue();
    }

    // -------------------------------------------------------------------------
    // floorToQuarterHour
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("floor: 317 → 315")
    void floor_317_to_315() {
        assertThat(OtRules.floorToQuarterHour(317)).isEqualTo(315);
    }

    @Test
    @DisplayName("floor: 314 → 300")
    void floor_314_to_300() {
        assertThat(OtRules.floorToQuarterHour(314)).isEqualTo(300);
    }

    @Test
    @DisplayName("floor: 300 → 300 (exact multiple, unchanged)")
    void floor_300_to_300() {
        assertThat(OtRules.floorToQuarterHour(300)).isEqualTo(300);
    }

    @Test
    @DisplayName("floor: 7 → 0")
    void floor_7_to_0() {
        assertThat(OtRules.floorToQuarterHour(7)).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // breakMinutesInsideWindow
    // Break schedule: morning tea 10:00–10:20, lunch 12:30–13:00, afternoon tea 15:00–15:10
    // -------------------------------------------------------------------------

    private static final List<BreakPeriod> BREAKS = List.of(
            new BreakPeriod(LocalTime.of(10,  0), LocalTime.of(10, 20), "Morning tea"),
            new BreakPeriod(LocalTime.of(12, 30), LocalTime.of(13,  0), "Lunch"),
            new BreakPeriod(LocalTime.of(15,  0), LocalTime.of(15, 10), "Afternoon tea")
    );

    @Test
    @DisplayName("breaks: window 08:00–12:30 → morning tea only (lunch boundary excluded) = 20 min")
    void breaks_window_0800_1230_morningTeaOnly() {
        int result = OtRules.breakMinutesInsideWindow(
                LocalTime.of(8, 0), LocalTime.of(12, 30), BREAKS);
        assertThat(result).isEqualTo(20);
    }

    @Test
    @DisplayName("breaks: window 07:25–13:20 (Saturday) → morning tea + lunch = 50 min")
    void breaks_window_saturday_0725_1320_morningTeaAndLunch() {
        int result = OtRules.breakMinutesInsideWindow(
                LocalTime.of(7, 25), LocalTime.of(13, 20), BREAKS);
        assertThat(result).isEqualTo(50);
    }

    @Test
    @DisplayName("breaks: window 13:00–16:25 → afternoon tea (15:00–15:10) inside → 10 min deducted")
    void breaks_window_1300_1625_afternoonTeaOnly() {
        int result = OtRules.breakMinutesInsideWindow(
                LocalTime.of(13, 0), LocalTime.of(16, 25), BREAKS);
        assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("breaks: window 06:00–07:30 (pre-shift) → no breaks inside = 0 min")
    void breaks_window_preShift_0600_0730_none() {
        int result = OtRules.breakMinutesInsideWindow(
                LocalTime.of(6, 0), LocalTime.of(7, 30), BREAKS);
        assertThat(result).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // builder helper
    // -------------------------------------------------------------------------

    private static DayInput input(boolean anyPerEmployeeSwitchSetToday, Boolean perEmployeeOtAuthorized) {
        return new DayInput(
                EmployeeCategory.STANDARD,
                LocalDate.of(2026, 6, 18),
                DayType.WEEKDAY,
                List.of(),
                true,
                perEmployeeOtAuthorized,
                anyPerEmployeeSwitchSetToday,
                List.of()
        );
    }
}
