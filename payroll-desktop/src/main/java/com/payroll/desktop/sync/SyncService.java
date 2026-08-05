package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;

import java.util.List;

/**
 * Runs a full sync: employees are pushed first (the cloud must know about an
 * employee before it can accept attendance for that employeeCode), and attendance
 * is only pushed once the employee push succeeds. If the cloud is unreachable or
 * the employee push itself fails, attendance sync is skipped for that run.
 */
public class SyncService {

    private final EmployeeRepository employeeRepo;
    private final AttendanceRecordRepository recordRepo;
    private final CloudSyncClient client;

    public SyncService(EmployeeRepository employeeRepo, AttendanceRecordRepository recordRepo, CloudSyncClient client) {
        this.employeeRepo = employeeRepo;
        this.recordRepo = recordRepo;
        this.client = client;
    }

    public SyncRunResult syncUnsyncedRecords() {
        if (!client.isCloudReachable()) {
            return new SyncRunResult(0, 0, 0, 0, 0, true, false);
        }

        List<Employee> employees = employeeRepo.findAll();
        EmployeeSyncPushResult employeeResult;
        try {
            employeeResult = client.pushEmployees(employees);
        } catch (SyncException e) {
            return new SyncRunResult(0, employees.size(), 0, 0, 0, false, true);
        }

        List<AttendanceRecord> pending = recordRepo.findUnsynced();
        int attempted = 0, synced = 0, failed = 0;

        for (AttendanceRecord record : pending) {
            attempted++;
            try {
                SyncPushResult result = client.pushRecord(record);
                recordRepo.markSynced(record.getId(), result.cloudRecordId());
                synced++;
            } catch (SyncException e) {
                recordRepo.incrementSyncAttempt(record.getId(), e.getMessage());
                failed++;
            }
        }

        return new SyncRunResult(employeeResult.synced(), employeeResult.rejected(),
                attempted, synced, failed, false, false);
    }
}
