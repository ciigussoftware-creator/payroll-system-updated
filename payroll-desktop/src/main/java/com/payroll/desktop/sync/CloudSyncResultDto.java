package com.payroll.desktop.sync;

/** Wire-format mirror of payroll-web-backend's AttendanceSyncResult. */
record CloudSyncResultDto(
        String syncUuid,
        String status,
        Long cloudRecordId,
        String reason
) {}
