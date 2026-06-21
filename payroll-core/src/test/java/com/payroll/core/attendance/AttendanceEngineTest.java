package com.payroll.core.attendance;

import com.payroll.core.entity.EmployeeCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    private static final List<BreakPeriod> STANDARD_BREAKS = List.of(
            new BreakPeriod(LocalTime.of(10,  0), LocalTime.of(10, 20), "Morning tea"),
            new BreakPeriod(LocalTime.of(12, 30), LocalTime.of(13,  0), "Lunch"),
            new BreakPeriod(LocalTime.of(15,  0), LocalTime.of(15, 10), "Afternoon tea")
    );

    /** Weekday STANDARD input with real break schedule and configurable OT authorization. */
    private DayInput weekdayOtInput(boolean anySwitch, Boolean perEmployeeAuth, List<Scan> scans) {
        return new DayInput(
                EmployeeCategory.STANDARD,
                DATE,
                DayType.WEEKDAY,
                scans,
                false,
                perEmployeeAuth,
                anySwitch,
                STANDARD_BREAKS);
    }

    private void assertAbsent(DayResult r) {
        assertThat(r.getDayClassification()).isEqualTo(DayClassification.ABSENT);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
    }

    private void assertOtDay(DayResult r) {
        assertThat(r.getDayClassification()).isEqualTo(DayClassification.OT_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    /** OT-day input with STANDARD_BREAKS and configurable auth. */
    private DayInput otDayInput(DayType dayType, boolean dayLevelOtOn,
                                boolean anySwitch, Boolean perEmployeeAuth, List<Scan> scans) {
        return new DayInput(
                EmployeeCategory.STANDARD,
                DATE,
                dayType,
                scans,
                dayLevelOtOn,
                perEmployeeAuth,
                anySwitch,
                STANDARD_BREAKS);
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

    // --- WEEKDAY (STANDARD) — post-shift OT ---

    @Test
    void weekday_ot_arriveAt0715_leave1630_15min() {
        // shiftEnd(07:15) = 16:15; OT = [16:15, 16:30] = 15 min; floor(15) = 15
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(7, 15), exit(16, 30))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isEqualTo(15);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0.25");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_postShiftOt_authorized_breakBeforeOtWindow_90min() {
        // arrive 07:00 → shiftEnd 16:00; leave 17:30 → 90 min raw
        // afternoon tea 15:00–15:10 is before 16:00, not in OT window → 0 deducted
        // floor(90) = 90 → 1.50 hrs
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(7, 0), exit(17, 30))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isEqualTo(90);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("1.5");
    }

    @Test
    void weekday_postShiftOt_notAuthorized_zeroOt() {
        // same scan as above but per-employee switches set today and this employee not included
        DayResult r = engine.classifyDay(
                weekdayOtInput(true, null, List.of(entry(7, 0), exit(17, 30))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
    }

    @Test
    void weekday_leaveExactlyAtShiftEnd_zeroOt() {
        // arrive 07:00 → shiftEnd 16:00; leave 16:00 → no time past shiftEnd
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(7, 0), exit(16, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
    }

    @Test
    void weekday_postShiftOt_flooredToQuarter_75min() {
        // arrive 07:00 → shiftEnd 16:00; leave 17:17 → 77 min raw → floor → 75 → 1.25 hrs
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(7, 0), exit(17, 17))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getOtMinutes()).isEqualTo(75);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("1.25");
    }

    // --- WEEKDAY (STANDARD) — pre-shift OT ---

    @Test
    void weekday_earlyArrival_noReturn_preShiftOtOnly() {
        // arrive 06:00, leave 10:00, never return → ABSENT, credit 0
        // OT slice [06:00, 07:30] = 90 min; no breaks before 07:30 → floor(90) = 90
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(6, 0), exit(10, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.ABSENT);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isEqualTo(90);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("1.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_earlyArrival_lateReturn_noHalfQualifies_preShiftOtOnly() {
        // arrive 06:00, return 12:30 (past PM half window 11:15–11:45) → ABSENT, credit 0
        // only the [06:00, 07:30] slice counts; [12:30, 16:30] starts after 07:30 → ignored
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(6, 0), exit(10, 0), entry(12, 30), exit(16, 30))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.ABSENT);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isEqualTo(90);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("1.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_absent_noPreShiftSession_zeroOt() {
        // arrive 08:30, leave 14:30 — misses full/half windows, no session before 07:30 → 0 OT
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(8, 30), exit(14, 30))));

        assertAbsent(r);
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_earlyArrival_shortSession_45minOt() {
        // arrive 06:00, leave 06:50 → 50 min raw before 07:30 → floor to 45 → ABSENT, credit 0
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(6, 0), exit(6, 50))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.ABSENT);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isEqualTo(45);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0.75");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_earlyArrival_noReturn_unauthorized_zeroOt() {
        // arrive 06:00, leave 10:00, unauthorized → 0 OT, ABSENT, credit 0
        DayResult r = engine.classifyDay(
                weekdayOtInput(true, null, List.of(entry(6, 0), exit(10, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.ABSENT);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_earlyArrival_morningSessionOtAgainstPmCredit() {
        // arrive 06:00, leave 10:00, return 11:30, leave 16:15 → PM half qualifies
        // entire morning session [06:00, 10:00] = 240 min outside PM credit [11:30, 16:15]
        // no breaks before 10:00 → floor(240) = 240 → 4.00 hrs
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(6, 0), exit(10, 0), entry(11, 30), exit(16, 15))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_PM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getOtMinutes()).isEqualTo(240);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("4.0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_earlyArrival_fullDayContinuous_preShiftOtCounts() {
        // arrive 06:00, work straight to 16:00 → FULL_DAY (shiftEnd(07:00)=16:00)
        // pre-shift [06:00, 07:30] = 90 min; post-shift = 0 (left at shiftEnd)
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(6, 0), exit(16, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isEqualTo(90);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("1.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_earlyArrival_unauthorized_zeroOt() {
        // same sessions as qualifyingPmSession but unauthorized → 0 OT, still HALF_DAY_PM credit
        DayResult r = engine.classifyDay(
                weekdayOtInput(true, null, List.of(entry(6, 0), exit(10, 0), entry(11, 30), exit(16, 15))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_PM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    // --- WEEKDAY (STANDARD) — half-day OT ---

    @Test
    void weekday_amHalf_noOtherSessions_zeroOt() {
        // single AM session — nothing outside credit span → 0 OT, credit 0.5
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(7, 0), exit(11, 15))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_AM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_pmHalf_noOtherSessions_zeroOt() {
        // single PM session [11:30, 16:15] — nothing outside credit span → 0 OT, credit 0.5
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null, List.of(entry(11, 30), exit(16, 15))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_PM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_amHalf_eveningReturn_120minOt() {
        // AM half: arrive 07:00, leave 11:15 (exactly 4h15m credit)
        // Evening return 17:00–19:00 = 120 min outside AM credit [07:00, 11:15]
        // no breaks in [17:00, 19:00] → floor(120) = 120 → 2.00 hrs
        DayResult r = engine.classifyDay(
                weekdayOtInput(false, null,
                        List.of(entry(7, 0), exit(11, 15), entry(17, 0), exit(19, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_AM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getOtMinutes()).isEqualTo(120);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("2.0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void weekday_amHalf_eveningReturn_unauthorized_zeroOt() {
        // same sessions as above but unauthorized → 0 OT, credit 0.5 still stands
        DayResult r = engine.classifyDay(
                weekdayOtInput(true, null,
                        List.of(entry(7, 0), exit(11, 15), entry(17, 0), exit(19, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.HALF_DAY_AM);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0.5");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    // --- WEEKDAY (PEELING) ---

    @Test
    void peeling_weekday_anyPresence_fullDay() {
        // smoke test — classification and credit only; OT depends on break schedule (tested separately)
        DayInput peelingInput = new DayInput(
                EmployeeCategory.PEELING,
                DATE,
                DayType.WEEKDAY,
                List.of(entry(8, 0), exit(16, 30)),
                false, null, false, List.of());

        DayResult r = engine.classifyDay(peelingInput);

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void peeling_weekday_authorized_210minOt() {
        // arrive 08:00, leave 16:30 → presence 510 min, midpoint 08:00+255=12:15
        // OT window [12:15, 16:30] = 255 min; lunch 12:30-13:00 = 30 min + afternoon tea 15:00-15:10 = 10 min
        // → 40 deducted → 215 raw → floor(215) = 210 → 3.5 hrs OT, credit 1.0
        DayInput input = new DayInput(
                EmployeeCategory.PEELING, DATE, DayType.WEEKDAY,
                List.of(entry(8, 0), exit(16, 30)),
                false, null, false, STANDARD_BREAKS);

        DayResult r = engine.classifyDay(input);

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isEqualTo(210);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("3.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void peeling_weekday_unauthorized_zeroOt() {
        // same sessions, unauthorized → 0 OT, credit 1.0 still
        DayInput input = new DayInput(
                EmployeeCategory.PEELING, DATE, DayType.WEEKDAY,
                List.of(entry(8, 0), exit(16, 30)),
                false, null, true, STANDARD_BREAKS);

        DayResult r = engine.classifyDay(input);

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
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

    // --- OT-day computation (Sunday / Saturday late / Mercantile holiday) ---

    @Test
    void sunday_authorized_480minOt() {
        // 08:00-17:00 = 540 min; morning tea 20 + lunch 30 + afternoon tea 10 = 60 deducted → 480; floor = 480
        DayResult r = engine.classifyDay(
                otDayInput(DayType.SUNDAY, false, false, null,
                        List.of(entry(8, 0), exit(17, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.OT_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isEqualTo(480);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("8.0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void sunday_unauthorized_zeroOt() {
        // same window but per-employee switches set and this employee not included → 0 OT
        DayResult r = engine.classifyDay(
                otDayInput(DayType.SUNDAY, false, true, null,
                        List.of(entry(8, 0), exit(17, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.OT_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isZero();
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("0");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void saturday_lateArrival_210minOt() {
        // arrive 09:00 > 08:00 → OT_DAY; 09:00-13:20 = 260 min
        // morning tea 10:00-10:20 = 20 min + lunch 12:30-13:00 = 30 min → 50 deducted → 210; floor = 210
        DayResult r = engine.classifyDay(
                otDayInput(DayType.SATURDAY, false, false, null,
                        List.of(entry(9, 0), exit(13, 20))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.OT_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isEqualTo(210);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("3.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void saturday_fullDay_postShiftOt_90min() {
        // arrive 07:30, leave 15:00 → FULL_DAY credit 1.0
        // post-shift [13:20, 15:00] = 100 min raw; no breaks after 13:20 → floor(100) = 90
        DayResult r = engine.classifyDay(
                otDayInput(DayType.SATURDAY, false, false, null,
                        List.of(entry(7, 30), exit(15, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("1.0");
        assertThat(r.getOtMinutes()).isEqualTo(90);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("1.5");
        assertThat(r.getFlags()).isEmpty();
    }

    @Test
    void mercantileHoliday_authorized_210minOt() {
        // 08:00-12:00 = 240 min; morning tea 10:00-10:20 inside → 20 deducted → 220; floor(220) = 210
        DayResult r = engine.classifyDay(
                otDayInput(DayType.MERCANTILE_HOLIDAY, true, false, null,
                        List.of(entry(8, 0), exit(12, 0))));

        assertThat(r.getDayClassification()).isEqualTo(DayClassification.OT_DAY);
        assertThat(r.getDayCredit()).isEqualByComparingTo("0");
        assertThat(r.getOtMinutes()).isEqualTo(210);
        assertThat(r.getOtHoursDecimal()).isEqualByComparingTo("3.5");
        assertThat(r.getFlags()).isEmpty();
    }
}
