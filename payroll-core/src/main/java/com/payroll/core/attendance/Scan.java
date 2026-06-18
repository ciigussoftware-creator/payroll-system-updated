package com.payroll.core.attendance;

import java.time.LocalDateTime;

public class Scan {

    private final LocalDateTime scanDatetime;
    private final ScanType scanType;

    public Scan(LocalDateTime scanDatetime, ScanType scanType) {
        this.scanDatetime = scanDatetime;
        this.scanType = scanType;
    }

    public LocalDateTime getScanDatetime() { return scanDatetime; }
    public ScanType getScanType() { return scanType; }
}
