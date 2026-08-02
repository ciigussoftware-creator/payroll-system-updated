package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;

import java.util.List;

public interface CloudSyncClient {
    SyncPushResult pushRecord(AttendanceRecord record) throws SyncException;
    EmployeeSyncPushResult pushEmployees(List<Employee> employees) throws SyncException;
    boolean isCloudReachable();
}
