package com.payroll.desktop.sync;

import com.payroll.core.entity.ScanType;

import java.time.LocalDateTime;

/** Wire-format mirror of payroll-web-backend's AttendanceSyncRequest. */
record CloudAttendanceRecordDto(
        String syncUuid,
        String employeeCode,
        LocalDateTime scanDatetime,
        ScanType scanType,
        LocalDateTime originalScanDatetime,
        String correctionNote,
        Long correctedBy
) {}
