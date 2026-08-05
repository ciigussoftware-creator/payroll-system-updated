package com.payroll.web.sync;

import com.payroll.core.entity.ScanType;

import java.time.LocalDateTime;

public record AttendanceSyncRequest(
        String syncUuid,
        String employeeCode,
        LocalDateTime scanDatetime,
        ScanType scanType,
        LocalDateTime originalScanDatetime,
        String correctionNote,
        Long correctedBy
) {}
