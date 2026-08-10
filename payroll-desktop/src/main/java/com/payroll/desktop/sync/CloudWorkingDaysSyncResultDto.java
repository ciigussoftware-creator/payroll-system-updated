package com.payroll.desktop.sync;

/** Wire-format mirror of payroll-web-backend's WorkingDaysSyncResult. */
record CloudWorkingDaysSyncResultDto(
        String periodMonth,
        String status,
        String reason
) {}
