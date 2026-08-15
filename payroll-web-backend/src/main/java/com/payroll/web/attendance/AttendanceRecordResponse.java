package com.payroll.web.attendance;

import com.payroll.core.entity.ScanType;

import java.time.LocalDateTime;

public record AttendanceRecordResponse(
        Long id,
        LocalDateTime scanDatetime,
        ScanType scanType,
        boolean missingClockOut
) {}
