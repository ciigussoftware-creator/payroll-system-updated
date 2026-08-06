package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.WorkingDaysConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class StubCloudSyncClient implements CloudSyncClient {

    private final Map<String, Long> pushedRecords = new HashMap<>();
    private final Set<String> failForUuids = new HashSet<>();
    private final List<String> callLog = new ArrayList<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    private boolean cloudReachable = true;
    private boolean failEmployeePush = false;
    private boolean failOtConfigPush = false;
    private boolean failOtAuthPush = false;
    private boolean failWorkingDaysPush = false;
    private int employeePushCount = 0;

    public void setCloudReachable(boolean reachable) {
        this.cloudReachable = reachable;
    }

    /** Makes pushRecord throw SyncException for the record with the given syncUuid. */
    public void setFailForUuid(String uuid) {
        failForUuids.add(uuid);
    }

    /** Makes pushEmployees throw SyncException instead of returning a result. */
    public void setFailEmployeePush(boolean fail) {
        this.failEmployeePush = fail;
    }

    /** Makes pushDayLevelOtConfigs throw SyncException instead of returning a result. */
    public void setFailOtConfigPush(boolean fail) {
        this.failOtConfigPush = fail;
    }

    /** Makes pushOtAuthorizations throw SyncException instead of returning a result. */
    public void setFailOtAuthPush(boolean fail) {
        this.failOtAuthPush = fail;
    }

    /** Makes pushWorkingDaysConfig throw SyncException instead of returning a result. */
    public void setFailWorkingDaysPush(boolean fail) {
        this.failWorkingDaysPush = fail;
    }

    /** Number of distinct syncUuids the stub has accepted (idempotent re-pushes don't increment). */
    public int getPushedCount() {
        return pushedRecords.size();
    }

    public int getEmployeePushCount() {
        return employeePushCount;
    }

    /** Ordered log of calls made ("employees:<n>" / "workingDays:<n>" / "otConfigs:<n>" / "otAuths:<n>" / "attendance:<uuid>") — used to assert call order. */
    public List<String> getCallLog() {
        return callLog;
    }

    @Override
    public boolean isCloudReachable() {
        return cloudReachable;
    }

    @Override
    public EmployeeSyncPushResult pushEmployees(List<Employee> employees) throws SyncException {
        callLog.add("employees:" + employees.size());
        employeePushCount++;
        if (failEmployeePush) {
            throw new SyncException("Stub: forced employee push failure");
        }
        return new EmployeeSyncPushResult(employees.size(), 0, 0, List.of());
    }

    @Override
    public WorkingDaysSyncPushResult pushWorkingDaysConfig(List<WorkingDaysConfig> configs) throws SyncException {
        callLog.add("workingDays:" + configs.size());
        if (failWorkingDaysPush) {
            throw new SyncException("Stub: forced working-days push failure");
        }
        return new WorkingDaysSyncPushResult(configs.size(), 0, 0, List.of());
    }

    @Override
    public DayLevelOtSyncPushResult pushDayLevelOtConfigs(List<DayLevelOTConfig> configs) throws SyncException {
        callLog.add("otConfigs:" + configs.size());
        if (failOtConfigPush) {
            throw new SyncException("Stub: forced OT config push failure");
        }
        return new DayLevelOtSyncPushResult(configs.size(), 0, 0, List.of());
    }

    @Override
    public OtAuthorizationSyncPushResult pushOtAuthorizations(List<OtAuthorizationRecord> authorizations) throws SyncException {
        callLog.add("otAuths:" + authorizations.size());
        if (failOtAuthPush) {
            throw new SyncException("Stub: forced OT authorization push failure");
        }
        return new OtAuthorizationSyncPushResult(authorizations.size(), 0, 0, List.of());
    }

    @Override
    public SyncPushResult pushRecord(AttendanceRecord record) throws SyncException {
        String uuid = record.getSyncUuid();
        callLog.add("attendance:" + uuid);
        if (failForUuids.contains(uuid)) {
            throw new SyncException("Stub: forced failure for uuid=" + uuid);
        }
        if (pushedRecords.containsKey(uuid)) {
            return new SyncPushResult(pushedRecords.get(uuid), true);
        }
        long cloudId = idCounter.getAndIncrement();
        pushedRecords.put(uuid, cloudId);
        return new SyncPushResult(cloudId, false);
    }
}
