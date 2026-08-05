package com.payroll.desktop.sync;

/** Wire-format mirror of payroll-web-backend's EmployeeSyncResult. */
record CloudEmployeeSyncResultDto(
        String employeeCode,
        String status,
        String reason
) {}
