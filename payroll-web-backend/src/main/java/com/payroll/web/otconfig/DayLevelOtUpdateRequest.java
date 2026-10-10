package com.payroll.web.otconfig;

import com.payroll.core.entity.DayType;

public record DayLevelOtUpdateRequest(
        boolean isAllStaffOt,
        DayType dayType,
        String reason
) {}
