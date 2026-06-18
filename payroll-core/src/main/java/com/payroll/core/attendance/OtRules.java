package com.payroll.core.attendance;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

public final class OtRules {

    private OtRules() {}

    public static boolean isOtAuthorized(DayInput input) {
        if (input.isAnyPerEmployeeSwitchSetToday()) {
            return Boolean.TRUE.equals(input.getPerEmployeeOtAuthorized());
        }
        return true;
    }

    public static int floorToQuarterHour(int rawMinutes) {
        return (rawMinutes / 15) * 15;
    }

    public static int breakMinutesInsideWindow(
            LocalTime windowStart, LocalTime windowEnd, List<BreakPeriod> breaks) {
        int total = 0;
        for (BreakPeriod b : breaks) {
            LocalTime overlapStart = b.getStart().isAfter(windowStart) ? b.getStart() : windowStart;
            LocalTime overlapEnd   = b.getEnd().isBefore(windowEnd)    ? b.getEnd()   : windowEnd;
            if (overlapStart.isBefore(overlapEnd)) {
                total += (int) Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        return total;
    }
}
