package com.payroll.desktop.sync;

import java.time.Instant;

/** Wire-format mirror of payroll-web-backend's WorkingDaysSyncRequest. */
record CloudWorkingDaysDto(
        String periodMonth,
        int availableWorkingDays,
        String updatedBy,
        Instant updatedAt
) {}
