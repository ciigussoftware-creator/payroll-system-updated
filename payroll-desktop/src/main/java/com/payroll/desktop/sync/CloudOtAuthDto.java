package com.payroll.desktop.sync;

import java.time.Instant;
import java.time.LocalDate;

/** Wire-format mirror of payroll-web-backend's OtAuthorizationSyncRequest. */
record CloudOtAuthDto(
        String employeeCode,
        LocalDate authDate,
        boolean authorized,
        String setBy,
        Instant setAt
) {}
