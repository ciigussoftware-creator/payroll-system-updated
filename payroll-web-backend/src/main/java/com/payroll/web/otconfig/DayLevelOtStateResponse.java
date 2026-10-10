package com.payroll.web.otconfig;

import com.payroll.core.entity.CloudDayLevelOTConfig;
import com.payroll.core.entity.DayType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Current (or, if nothing has been explicitly set, derived) day-level OT switch state for a
 * company+date. The derived default mirrors the desktop's {@code OtSwitchService.deriveDayType}
 * calendar rule (Sunday vs. weekday) and additionally reports {@code isAllStaffOt=true} for a
 * derived Sunday — matching {@code AttendanceEngine.classifySunday}, which grants OT on Sundays
 * regardless of this switch, so the web UI shows the state that is actually in effect rather
 * than a misleading "off" for a day nobody has touched yet.
 */
public record DayLevelOtStateResponse(
        boolean isAllStaffOt,
        DayType dayType,
        Instant setAt,
        Long setBy
) {
    static DayLevelOtStateResponse from(CloudDayLevelOTConfig config) {
        return new DayLevelOtStateResponse(
                config.isAllStaffOt(), config.getDayType(), config.getSetAt(), config.getSetBy());
    }

    static DayLevelOtStateResponse derived(LocalDate date) {
        DayType dayType = date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? DayType.SUNDAY : DayType.WEEKDAY;
        boolean isAllStaffOt = dayType == DayType.SUNDAY;
        return new DayLevelOtStateResponse(isAllStaffOt, dayType, null, null);
    }
}
