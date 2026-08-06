package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.WorkingDaysConfig;

import java.util.List;

public interface CloudSyncClient {
    SyncPushResult pushRecord(AttendanceRecord record) throws SyncException;
    EmployeeSyncPushResult pushEmployees(List<Employee> employees) throws SyncException;
    DayLevelOtSyncPushResult pushDayLevelOtConfigs(List<DayLevelOTConfig> configs) throws SyncException;
    OtAuthorizationSyncPushResult pushOtAuthorizations(List<OtAuthorizationRecord> authorizations) throws SyncException;
    WorkingDaysSyncPushResult pushWorkingDaysConfig(List<WorkingDaysConfig> configs) throws SyncException;
    boolean isCloudReachable();
}
