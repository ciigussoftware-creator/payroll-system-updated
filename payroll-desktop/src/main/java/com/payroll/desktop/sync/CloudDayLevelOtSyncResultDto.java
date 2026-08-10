package com.payroll.desktop.sync;

import java.time.LocalDate;

/** Wire-format mirror of payroll-web-backend's DayLevelOtSyncResult. */
record CloudDayLevelOtSyncResultDto(
        LocalDate configDate,
        String status,
        String reason
) {}
