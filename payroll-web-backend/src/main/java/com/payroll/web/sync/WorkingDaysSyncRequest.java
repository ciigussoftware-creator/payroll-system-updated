package com.payroll.web.sync;

import java.time.Instant;

public record WorkingDaysSyncRequest(
        String periodMonth,
        int availableWorkingDays,
        String updatedBy,
        Instant updatedAt
) {}
