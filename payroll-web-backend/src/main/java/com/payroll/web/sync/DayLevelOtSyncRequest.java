package com.payroll.web.sync;

import com.payroll.core.entity.DayType;

import java.time.Instant;
import java.time.LocalDate;

public record DayLevelOtSyncRequest(
        LocalDate configDate,
        boolean isAllStaffOt,
        DayType dayType,
        Long setBy,
        Instant setAt
) {}
