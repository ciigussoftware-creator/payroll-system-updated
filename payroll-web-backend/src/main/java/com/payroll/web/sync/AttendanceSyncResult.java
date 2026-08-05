package com.payroll.web.sync;

public record AttendanceSyncResult(
        String syncUuid,
        SyncStatus status,
        Long cloudRecordId,
        String reason
) {
    static AttendanceSyncResult accepted(String syncUuid, Long cloudRecordId) {
        return new AttendanceSyncResult(syncUuid, SyncStatus.ACCEPTED, cloudRecordId, null);
    }

    static AttendanceSyncResult updated(String syncUuid, Long cloudRecordId) {
        return new AttendanceSyncResult(syncUuid, SyncStatus.UPDATED, cloudRecordId, null);
    }

    static AttendanceSyncResult noOp(String syncUuid, Long cloudRecordId) {
        return new AttendanceSyncResult(syncUuid, SyncStatus.NO_OP, cloudRecordId, null);
    }

    static AttendanceSyncResult rejected(String syncUuid, String reason) {
        return new AttendanceSyncResult(syncUuid, SyncStatus.REJECTED, null, reason);
    }
}
