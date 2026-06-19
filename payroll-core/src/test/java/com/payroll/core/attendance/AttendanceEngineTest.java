package com.payroll.core.attendance;

import com.payroll.core.entity.EmployeeCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceEngineTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);

    private final AttendanceEngine engine = new AttendanceEngine();

    // --- helpers ---

    private DayInput input(DayType dayType, boolean dayLevelOtOn, List<Scan> scans) {
        return new DayInput(
                EmployeeCategory.STANDARD,
                DATE,
                dayType,
                scans,
                dayLevelOtOn,
                null,
                false,
                List.of());
    }

    private static Scan entry(int h, int m) { return new Scan(DATE.atTime(h, m), ScanType.ENTRY); }
    private static Scan exit(int h, int m)  { return new Scan(DATE.atTime(h, m), ScanType.EXIT); }

    private void assertAbsent(DayResult r) {
        assertThat(r.getDayClassification()).isEqualTo(DayClassification.ABSENT);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
    }

    private void assertOtDay(DayResult r) {
        assertThat(r.getDayClassification()).isEqualTo(DayClassification.OT_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    // --- early-exit cases (from 2D-2) ---

    @Test
    void missingClockOut_flaggedAbsentCreditZero() {
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(8, 0))));

        assertAbsent(r);
        assertThat(r.getFlags()).containsExactly(DayFlag.MISSING_CLOCK_OUT);
    }

    @Test
    void zeroScans_absentCreditZeroNoFlags() {
        DayResult r = engine.classifyDay(input(DayType.WEEKDAY, false, List.of()));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    // --- SUNDAY ---

    @Test
    void sunday_withPresence_otDay() {
        DayResult r = engine.classifyDay(
                input(DayType.SUNDAY, false, List.of(entry(8, 0), exit(12, 0))));

        assertOtDay(r);
    }

    @Test
    void sunday_noPresence_absent() {
        DayResult r = engine.classifyDay(input(DayType.SUNDAY, false, List.of()));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    // --- SATURDAY ---

    @Test
    void saturday_arriveOnTime_reachEnd_fullDay() {
        // arrive 07:30, exit 13:20 — at/before 08:00, reaches SATURDAY_END
        DayResult r = engine.classifyDay(
                input(DayType.SATURDAY, false, List.of(entry(7, 30), exit(13, 20))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void saturday_arriveLate_otDay() {
        // arrive 09:00 — after 08:00
        DayResult r = engine.classifyDay(
                input(DayType.SATURDAY, false, List.of(entry(9, 0), exit(13, 20))));

        assertOtDay(r);
    }

    // --- WEEKDAY (STANDARD) — full day ---

    @Test
    void weekday_fullDay_arriveOnTime_workFullShift() {
        // arrive 07:30, shiftEnd = 16:30 → FULL_DAY
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(7, 30), exit(16, 30))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getFlags()).isEmpty();
    }

    // --- WEEKDAY (STANDARD) — AM half-day ---

    @Test
    void weekday_amHalf_earliestArrival_exactDuration() {
        // arrive 07:00, leave 11:15 — exactly 4h15m → HALF_DAY_AM
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(7, 0), exit(11, 15))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_AM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_amHalf_typicalArrival() {
        // arrive 07:30, leave 11:45 — 4h15m → HALF_DAY_AM
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(7, 30), exit(11, 45))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_AM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_amHalf_latestArrival_exactDuration() {
        // arrive 08:00, leave 12:15 — exactly 4h15m → HALF_DAY_AM
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(8, 0), exit(12, 15))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_AM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_absent_amArrival_shortDuration() {
        // arrive 07:30, leave 11:30 — only 4h, short of 4h15m → ABSENT
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(7, 30), exit(11, 30))));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_absent_arrivedAfterAmWindow() {
        // arrive 08:15, left 12:30 — outside AM arrival window → ABSENT
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(8, 15), exit(12, 30))));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    // --- WEEKDAY (STANDARD) — PM half-day ---

    @Test
    void weekday_pmHalf_earliestStart_exactDuration() {
        // start 11:15, leave 16:00 — exactly 4h45m → HALF_DAY_PM
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(11, 15), exit(16, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_PM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_pmHalf_latestStart_exactDuration() {
        // start 11:45, leave 16:30 — exactly 4h45m → HALF_DAY_PM
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(11, 45), exit(16, 30))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_PM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_absent_pmStart_shortDuration() {
        // start 11:15, leave 15:30 — short of 4h45m → ABSENT
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(11, 15), exit(15, 30))));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_absent_startedAfterPmWindow() {
        // start 12:00, outside PM window → ABSENT
        DayResult r = engine.classifyDay(
                input(DayType.WEEKDAY, false, List.of(entry(12, 0), exit(16, 45))));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    // --- WEEKDAY (PEELING) ---

    @Test
    void peeling_weekday_anyPresence_fullDay() {
        DayInput peelingInput = new DayInput(
                EmployeeCategory.PEELING,
                DATE,
                DayType.WEEKDAY,
                List.of(entry(8, 0), exit(16, 30)),
                false, null, false, List.of());

        DayResult r = engine.classifyDay(peelingInput);

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getFlags()).isEmpty();
    }

    // --- MERCANTILE HOLIDAY ---

    @Test
    void mercantileHoliday_dayLevelOtOn_withPresence_otDay() {
        DayResult r = engine.classifyDay(
                input(DayType.MERCANTILE_HOLIDAY, true, List.of(entry(8, 0), exit(12, 0))));

        assertOtDay(r);
    }

    @Test
    void mercantileHoliday_dayLevelOtOff_absent() {
        DayResult r = engine.classifyDay(
                input(DayType.MERCANTILE_HOLIDAY, false, List.of(entry(8, 0), exit(12, 0))));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }
}
