package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;

public interface CloudSyncClient {
    SyncPushResult pushRecord(AttendanceRecord record) throws SyncException;
    boolean isCloudReachable();
}
