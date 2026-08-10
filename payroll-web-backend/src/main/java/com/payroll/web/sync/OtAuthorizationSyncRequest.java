package com.payroll.web.sync;

import java.time.Instant;
import java.time.LocalDate;

public record OtAuthorizationSyncRequest(
        String employeeCode,
        LocalDate authDate,
        boolean authorized,
        String setBy,
        Instant setAt
) {}
