package com.payroll.desktop.sync;

import com.payroll.core.entity.DayType;

import java.time.Instant;
import java.time.LocalDate;

/** Wire-format mirror of payroll-web-backend's DayLevelOtSyncRequest. */
record CloudDayLevelOtDto(
        LocalDate configDate,
        boolean isAllStaffOt,
        DayType dayType,
        Long setBy,
        Instant setAt
) {}
