package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class StubCloudSyncClient implements CloudSyncClient {

    private final Map<String, Long> pushedRecords = new HashMap<>();
    private final Set<String> failForUuids = new HashSet<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    private boolean cloudReachable = true;

    public void setCloudReachable(boolean reachable) {
        this.cloudReachable = reachable;
    }

    /** Makes pushRecord throw SyncException for the record with the given syncUuid. */
    public void setFailForUuid(String uuid) {
        failForUuids.add(uuid);
    }

    /** Number of distinct syncUuids the stub has accepted (idempotent re-pushes don't increment). */
    public int getPushedCount() {
        return pushedRecords.size();
    }

    @Override
    public boolean isCloudReachable() {
        return cloudReachable;
    }

    @Override
    public SyncPushResult pushRecord(AttendanceRecord record) throws SyncException {
        String uuid = record.getSyncUuid();
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
