package com.payroll.core.attendance;

import com.payroll.core.entity.EmployeeCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public class AttendanceEngine {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static final DayResult ABSENT_CLEAN =
            new DayResult(DayClassification.ABSENT, BigDecimal.ZERO, 0, BigDecimal.ZERO, Set.of());

    private static final DayResult OT_CLEAN =
            new DayResult(DayClassification.OT_DAY, BigDecimal.ZERO, 0, BigDecimal.ZERO, Set.of());

    private static final DayResult FULL_DAY_CLEAN =
            new DayResult(DayClassification.FULL_DAY, BigDecimal.ONE, 0, BigDecimal.ZERO, Set.of());

    private static final DayResult HALF_DAY_AM_CLEAN =
            new DayResult(DayClassification.HALF_DAY_AM, HALF, 0, BigDecimal.ZERO, Set.of());

    private static final DayResult HALF_DAY_PM_CLEAN =
            new DayResult(DayClassification.HALF_DAY_PM, HALF, 0, BigDecimal.ZERO, Set.of());

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
                                        : classifyWeekday(sr.getSessions());
        };
    }

    private DayResult classifyPeeling() {
        // Any presence (guaranteed by caller) → full day credit; OT from second half computed in Phase 2E
        return FULL_DAY_CLEAN;
    }

    private DayResult classifyWeekday(List<Session> sessions) {
        LocalDateTime firstStart = sessions.get(0).getStart();
        LocalTime arrivalTime    = firstStart.toLocalTime();
        LocalDateTime lastEnd    = sessions.get(sessions.size() - 1).getEnd();

        // FULL_DAY: arrival in flex window [07:00, 08:00] AND presence reaches shift end (arrival + 9h)
        if (!arrivalTime.isBefore(AttendanceConstants.FLEX_ARRIVAL_START)
                && !arrivalTime.isAfter(AttendanceConstants.FLEX_ARRIVAL_END)
                && !lastEnd.isBefore(AttendanceConstants.shiftEnd(firstStart))) {
            return FULL_DAY_CLEAN;
        }

        // AM_HALF: first session start in [07:00, 08:00] AND duration >= 4h15m
        if (!arrivalTime.isBefore(AttendanceConstants.AM_HALF_ARRIVAL_START)
                && !arrivalTime.isAfter(AttendanceConstants.AM_HALF_ARRIVAL_END)
                && !sessions.get(0).getEnd().isBefore(firstStart.plus(AttendanceConstants.AM_HALF_DURATION))) {
            return HALF_DAY_AM_CLEAN;
        }

        // PM_HALF: any session start in [11:15, 11:45] AND duration >= 4h45m
        for (Session s : sessions) {
            LocalTime sStart = s.getStart().toLocalTime();
            if (!sStart.isBefore(AttendanceConstants.PM_HALF_START_EARLIEST)
                    && !sStart.isAfter(AttendanceConstants.PM_HALF_START_LATEST)
                    && !s.getEnd().isBefore(s.getStart().plus(AttendanceConstants.PM_HALF_DURATION))) {
                return HALF_DAY_PM_CLEAN;
            }
        }

        return ABSENT_CLEAN;
    }

    private DayResult classifySaturday(List<Session> sessions) {
        LocalTime arrival  = sessions.get(0).getStart().toLocalTime();
        LocalTime lastExit = sessions.get(sessions.size() - 1).getEnd().toLocalTime();

        if (!arrival.isAfter(AttendanceConstants.FLEX_ARRIVAL_END)
                && !lastExit.isBefore(AttendanceConstants.SATURDAY_END)) {
            return FULL_DAY_CLEAN;
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
