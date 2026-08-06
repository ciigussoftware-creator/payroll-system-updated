package com.payroll.desktop.sync;

import java.time.LocalDate;

/** Wire-format mirror of payroll-web-backend's OtAuthorizationSyncResult. */
record CloudOtAuthSyncResultDto(
        String employeeCode,
        LocalDate authDate,
        String status,
        String reason
) {}
