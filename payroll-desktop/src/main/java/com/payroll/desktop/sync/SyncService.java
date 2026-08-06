package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.OtEmployeeAuthorization;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a full sync in dependency order: employees first (the cloud must know about an
 * employee before it can accept attendance or OT authorizations for that employeeCode),
 * then day-level OT configs and OT authorizations (so the cloud's OT-pay calculation has
 * the authorization switches available before or alongside attendance), and finally
 * attendance. A failure pushing employees or either OT data set skips attendance for that
 * run, since the cloud can't yet correctly interpret attendance without it.
 */
public class SyncService {

    private final EmployeeRepository employeeRepo;
    private final DayLevelOTConfigRepository dayLevelOTConfigRepo;
    private final OtEmployeeAuthorizationRepository otAuthRepo;
    private final AttendanceRecordRepository recordRepo;
    private final CloudSyncClient client;

    public SyncService(EmployeeRepository employeeRepo,
                        DayLevelOTConfigRepository dayLevelOTConfigRepo,
                        OtEmployeeAuthorizationRepository otAuthRepo,
                        AttendanceRecordRepository recordRepo,
                        CloudSyncClient client) {
        this.employeeRepo = employeeRepo;
        this.dayLevelOTConfigRepo = dayLevelOTConfigRepo;
        this.otAuthRepo = otAuthRepo;
        this.recordRepo = recordRepo;
        this.client = client;
    }

    public SyncRunResult syncUnsyncedRecords() {
        if (!client.isCloudReachable()) {
            return new SyncRunResult(0, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false);
        }

        List<Employee> employees = employeeRepo.findAll();
        EmployeeSyncPushResult employeeResult;
        try {
            employeeResult = client.pushEmployees(employees);
        } catch (SyncException e) {
            return new SyncRunResult(0, employees.size(), 0, 0, 0, 0, 0, 0, 0, false, true, false);
        }

        List<DayLevelOTConfig> otConfigs = dayLevelOTConfigRepo.findAll();
        DayLevelOtSyncPushResult otConfigResult;
        try {
            otConfigResult = client.pushDayLevelOtConfigs(otConfigs);
        } catch (SyncException e) {
            return new SyncRunResult(employeeResult.synced(), employeeResult.rejected(),
                    0, otConfigs.size(), 0, 0, 0, 0, 0, false, false, true);
        }

        List<OtEmployeeAuthorization> otAuths = otAuthRepo.findAll();
        OtAuthorizationSyncPushResult otAuthResult;
        try {
            otAuthResult = client.pushOtAuthorizations(toOtAuthorizationRecords(otAuths, employees));
        } catch (SyncException e) {
            return new SyncRunResult(employeeResult.synced(), employeeResult.rejected(),
                    otConfigResult.synced(), otConfigResult.rejected(),
                    0, otAuths.size(), 0, 0, 0, false, false, true);
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
                otConfigResult.synced(), otConfigResult.rejected(),
                otAuthResult.synced(), otAuthResult.rejected(),
                attempted, synced, failed, false, false, false);
    }

    private List<OtAuthorizationRecord> toOtAuthorizationRecords(List<OtEmployeeAuthorization> auths,
                                                                   List<Employee> employees) {
        Map<Long, String> codeById = new HashMap<>();
        for (Employee employee : employees) {
            codeById.put(employee.getId(), employee.getEmployeeCode());
        }

        return auths.stream()
                .filter(a -> codeById.containsKey(a.getEmployeeId()))
                .map(a -> new OtAuthorizationRecord(
                        codeById.get(a.getEmployeeId()),
                        a.getDate(),
                        a.isAuthorized(),
                        a.getSetBy(),
                        a.getSetAt()))
                .toList();
    }
}
