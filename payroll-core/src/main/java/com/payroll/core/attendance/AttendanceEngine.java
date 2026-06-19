package com.payroll.core.attendance;

import com.payroll.core.entity.EmployeeCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public class AttendanceEngine {

    private static final BigDecimal HALF  = new BigDecimal("0.5");
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private static final DayResult ABSENT_CLEAN =
            new DayResult(DayClassification.ABSENT, BigDecimal.ZERO, 0, BigDecimal.ZERO, Set.of());

    private static final DayResult OT_CLEAN =
            new DayResult(DayClassification.OT_DAY, BigDecimal.ZERO, 0, BigDecimal.ZERO, Set.of());

    private final SessionBuilder sessionBuilder = new SessionBuilder();

    public DayResult classifyDay(DayInput input) {
        SessionResult sr = sessionBuilder.buildSessions(input.getScans());

        if (sr.hasMissingClockOut()) {
            return new DayResult(
                    DayClassification.ABSENT,
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO,
                    Set.of(DayFlag.MISSING_CLOCK_OUT));
        }

        if (sr.getSessions().isEmpty()) {
            return ABSENT_CLEAN;
        }

        return switch (input.getDayType()) {
            case SUNDAY             -> OT_CLEAN;
            case SATURDAY           -> classifySaturday(sr.getSessions());
            case MERCANTILE_HOLIDAY -> classifyMercantileHoliday(input.isDayLevelOtOn());
            case SPECIAL            -> ABSENT_CLEAN; // TODO: SPECIAL branch
            case WEEKDAY            -> input.getCategory() == EmployeeCategory.PEELING
                                        ? classifyPeeling()
                                        : classifyWeekday(input, sr.getSessions());
        };
    }

    private DayResult classifyPeeling() {
        // TODO: OT from second half computed in Phase 2E
        return new DayResult(DayClassification.FULL_DAY, BigDecimal.ONE, 0, BigDecimal.ZERO, Set.of());
    }

    private DayResult classifyWeekday(DayInput input, List<Session> sessions) {
        LocalDateTime firstStart = sessions.get(0).getStart();
        LocalTime arrivalTime    = firstStart.toLocalTime();

        // Early arrival: entry strictly before the flex window (07:00). Pre-shift OT is conditional.
        if (arrivalTime.isBefore(AttendanceConstants.FLEX_ARRIVAL_START)) {
            return classifyWeekdayEarly(input, sessions, firstStart);
        }

        // Normal path (arrival within or after flex window)
        LocalDateTime shiftEnd = AttendanceConstants.shiftEnd(firstStart);

        // FULL_DAY: arrival session must run continuously through shiftEnd.
        // A gap (employee left and returned) disqualifies — only the first session's end is checked.
        if (!arrivalTime.isAfter(AttendanceConstants.FLEX_ARRIVAL_END)
                && !sessions.get(0).getEnd().isBefore(shiftEnd)) {
            return buildFullDayResult(input, firstStart, shiftEnd, sessions);
        }

        if (!arrivalTime.isAfter(AttendanceConstants.AM_HALF_ARRIVAL_END)
                && !sessions.get(0).getEnd().isBefore(firstStart.plus(AttendanceConstants.AM_HALF_DURATION))) {
            LocalDateTime creditEnd = firstStart.plus(AttendanceConstants.AM_HALF_DURATION);
            return buildHalfDayResult(input, DayClassification.HALF_DAY_AM, firstStart, creditEnd, sessions);
        }

        for (Session s : sessions) {
            LocalTime sStart = s.getStart().toLocalTime();
            if (!sStart.isBefore(AttendanceConstants.PM_HALF_START_EARLIEST)
                    && !sStart.isAfter(AttendanceConstants.PM_HALF_START_LATEST)
                    && !s.getEnd().isBefore(s.getStart().plus(AttendanceConstants.PM_HALF_DURATION))) {
                LocalDateTime creditEnd = s.getStart().plus(AttendanceConstants.PM_HALF_DURATION);
                return buildHalfDayResult(input, DayClassification.HALF_DAY_PM, s.getStart(), creditEnd, sessions);
            }
        }

        return ABSENT_CLEAN;
    }

    /**
     * Handles the case where the first session starts before 07:00.
     * Credit classification uses 07:00 as the effective start of the early session.
     * OT = worked minutes outside the earned credit span across all sessions.
     */
    private DayResult classifyWeekdayEarly(DayInput input, List<Session> sessions, LocalDateTime earlyStart) {
        LocalDate date           = earlyStart.toLocalDate();
        LocalDateTime adjStart   = date.atTime(AttendanceConstants.FLEX_ARRIVAL_START);    // 07:00
        LocalDateTime shiftBound = date.atTime(AttendanceConstants.SHIFT_START_BOUNDARY); // 07:30
        LocalDateTime shiftEnd   = AttendanceConstants.shiftEnd(adjStart);                // 16:00
        LocalDateTime earlyEnd   = sessions.get(0).getEnd();
        LocalDateTime lastEnd    = sessions.get(sessions.size() - 1).getEnd();

        // FULL_DAY: early session's portion from 07:00 runs through shiftEnd (16:00)
        if (!earlyEnd.isBefore(shiftEnd)) {
            int preOt  = computeNetOtMinutes(input, earlyStart, shiftBound);
            int postOt = computeNetOtMinutes(input, shiftEnd, lastEnd);
            return fullDayResult(preOt + postOt);
        }

        // AM_HALF: early session's portion from 07:00 lasts >= 4h15m
        // OT = everything outside the AM credit span [07:00, 07:00+4h15m]
        if (!earlyEnd.isBefore(adjStart.plus(AttendanceConstants.AM_HALF_DURATION))) {
            LocalDateTime creditEnd = adjStart.plus(AttendanceConstants.AM_HALF_DURATION);
            return buildHalfDayResult(input, DayClassification.HALF_DAY_AM, adjStart, creditEnd, sessions);
        }

        // PM_HALF: any session starts in [11:15, 11:45] and lasts >= 4h45m
        // OT = everything outside the PM credit span [pmStart, pmStart+4h45m]
        for (Session s : sessions) {
            LocalTime sStart = s.getStart().toLocalTime();
            if (!sStart.isBefore(AttendanceConstants.PM_HALF_START_EARLIEST)
                    && !sStart.isAfter(AttendanceConstants.PM_HALF_START_LATEST)
                    && !s.getEnd().isBefore(s.getStart().plus(AttendanceConstants.PM_HALF_DURATION))) {
                LocalDateTime creditEnd = s.getStart().plus(AttendanceConstants.PM_HALF_DURATION);
                return buildHalfDayResult(input, DayClassification.HALF_DAY_PM, s.getStart(), creditEnd, sessions);
            }
        }

        // No qualifying session → pre-shift OT forfeited
        return ABSENT_CLEAN;
    }

    // -------------------------------------------------------------------------
    // Result builders
    // -------------------------------------------------------------------------

    private DayResult buildFullDayResult(DayInput input,
                                         LocalDateTime creditStart, LocalDateTime creditEnd,
                                         List<Session> sessions) {
        return fullDayResult(computeOtOutsideCredit(input, creditStart, creditEnd, sessions));
    }

    private DayResult buildHalfDayResult(DayInput input, DayClassification cls,
                                          LocalDateTime creditStart, LocalDateTime creditEnd,
                                          List<Session> sessions) {
        return halfDayResult(cls, computeOtOutsideCredit(input, creditStart, creditEnd, sessions));
    }

    private DayResult fullDayResult(int otMinutes) {
        BigDecimal otHours = BigDecimal.valueOf(otMinutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
        return new DayResult(DayClassification.FULL_DAY, BigDecimal.ONE, otMinutes, otHours, Set.of());
    }

    private DayResult halfDayResult(DayClassification cls, int otMinutes) {
        BigDecimal otHours = BigDecimal.valueOf(otMinutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
        return new DayResult(cls, HALF, otMinutes, otHours, Set.of());
    }

    // -------------------------------------------------------------------------
    // OT computation
    // -------------------------------------------------------------------------

    /**
     * Sum of worked session minutes falling outside [creditStart, creditEnd],
     * with break deduction per sub-window, then a single 15-min floor and auth gate.
     */
    private int computeOtOutsideCredit(DayInput input,
                                        LocalDateTime creditStart, LocalDateTime creditEnd,
                                        List<Session> sessions) {
        int totalRaw = 0;
        for (Session s : sessions) {
            if (s.getStart().isBefore(creditStart)) {
                LocalDateTime otEnd = s.getEnd().isBefore(creditStart) ? s.getEnd() : creditStart;
                totalRaw += rawNetMinutes(input, s.getStart(), otEnd);
            }
            if (s.getEnd().isAfter(creditEnd)) {
                LocalDateTime otStart = s.getStart().isAfter(creditEnd) ? s.getStart() : creditEnd;
                totalRaw += rawNetMinutes(input, otStart, s.getEnd());
            }
        }
        int ot = OtRules.floorToQuarterHour(totalRaw);
        return OtRules.isOtAuthorized(input) ? ot : 0;
    }

    /** Raw session minutes in [start, end] minus breaks — no floor, no auth gate. */
    private int rawNetMinutes(DayInput input, LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) return 0;
        int raw = (int) Duration.between(start, end).toMinutes();
        raw -= OtRules.breakMinutesInsideWindow(start.toLocalTime(), end.toLocalTime(),
                input.getBreakSchedule());
        return Math.max(raw, 0);
    }

    /** Net OT minutes for a single window: raw − breaks, floored, zeroed if unauthorized.
     *  Used by the early-arrival FULL_DAY pre/post-shift path only. */
    private int computeNetOtMinutes(DayInput input, LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (!windowEnd.isAfter(windowStart)) return 0;
        int raw = (int) Duration.between(windowStart, windowEnd).toMinutes();
        raw -= OtRules.breakMinutesInsideWindow(
                windowStart.toLocalTime(), windowEnd.toLocalTime(), input.getBreakSchedule());
        raw = OtRules.floorToQuarterHour(raw);
        return OtRules.isOtAuthorized(input) ? raw : 0;
    }

    // -------------------------------------------------------------------------
    // Non-weekday branches
    // -------------------------------------------------------------------------

    private DayResult classifySaturday(List<Session> sessions) {
        LocalTime arrival  = sessions.get(0).getStart().toLocalTime();
        LocalTime lastExit = sessions.get(sessions.size() - 1).getEnd().toLocalTime();

        if (!arrival.isAfter(AttendanceConstants.FLEX_ARRIVAL_END)
                && !lastExit.isBefore(AttendanceConstants.SATURDAY_END)) {
            return new DayResult(DayClassification.FULL_DAY, BigDecimal.ONE, 0, BigDecimal.ZERO, Set.of());
        }

        if (arrival.isAfter(AttendanceConstants.FLEX_ARRIVAL_END)) {
            return OT_CLEAN;
        }

        // TODO: arrived on time but left before SATURDAY_END — no business rule yet
        return ABSENT_CLEAN;
    }

    private DayResult classifyMercantileHoliday(boolean dayLevelOtOn) {
        return dayLevelOtOn ? OT_CLEAN : ABSENT_CLEAN;
    }
}
