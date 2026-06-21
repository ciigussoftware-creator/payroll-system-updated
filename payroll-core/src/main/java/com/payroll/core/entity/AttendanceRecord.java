package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "scan_datetime", nullable = false)
    private LocalDateTime scanDatetime;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private ScanType scanType;

    @Column(name = "is_ot_authorized", nullable = false)
    private boolean isOtAuthorized = false;

    @Column(name = "ot_authorized_by")
    private Long otAuthorizedBy;

    @Column(name = "correction_note")
    private String correctionNote;

    @Column(name = "original_scan_datetime")
    private LocalDateTime originalScanDatetime;

    @Column(name = "corrected_by")
    private Long correctedBy;

    @Column(name = "synced_to_cloud", nullable = false)
    private boolean syncedToCloud = false;

    @Column(name = "cloud_record_id")
    private Long cloudRecordId;

    @Column(name = "sync_uuid", nullable = false, unique = true, length = 36)
    private String syncUuid;

    @Column(name = "sync_attempts", nullable = false)
    private int syncAttempts = 0;

    @Column(name = "last_sync_error", length = 1000)
    private String lastSyncError;

    @Column(name = "last_sync_attempt_at")
    private Instant lastSyncAttemptAt;

    public AttendanceRecord() {
        this.syncUuid = UUID.randomUUID().toString();
    }

    @PrePersist
    private void prePersist() {
        if (syncUuid == null) {
            syncUuid = UUID.randomUUID().toString();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public LocalDateTime getScanDatetime() { return scanDatetime; }
    public void setScanDatetime(LocalDateTime scanDatetime) { this.scanDatetime = scanDatetime; }

    public ScanType getScanType() { return scanType; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }

    public boolean isOtAuthorized() { return isOtAuthorized; }
    public void setOtAuthorized(boolean otAuthorized) { isOtAuthorized = otAuthorized; }

    public Long getOtAuthorizedBy() { return otAuthorizedBy; }
    public void setOtAuthorizedBy(Long otAuthorizedBy) { this.otAuthorizedBy = otAuthorizedBy; }

    public String getCorrectionNote() { return correctionNote; }
    public void setCorrectionNote(String correctionNote) { this.correctionNote = correctionNote; }

    public LocalDateTime getOriginalScanDatetime() { return originalScanDatetime; }
    public void setOriginalScanDatetime(LocalDateTime originalScanDatetime) { this.originalScanDatetime = originalScanDatetime; }

    public Long getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(Long correctedBy) { this.correctedBy = correctedBy; }

    public boolean isSyncedToCloud() { return syncedToCloud; }
    public void setSyncedToCloud(boolean syncedToCloud) { this.syncedToCloud = syncedToCloud; }

    public Long getCloudRecordId() { return cloudRecordId; }
    public void setCloudRecordId(Long cloudRecordId) { this.cloudRecordId = cloudRecordId; }

    public String getSyncUuid() { return syncUuid; }
    public void setSyncUuid(String syncUuid) { this.syncUuid = syncUuid; }

    public int getSyncAttempts() { return syncAttempts; }
    public void setSyncAttempts(int syncAttempts) { this.syncAttempts = syncAttempts; }

    public String getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }

    public Instant getLastSyncAttemptAt() { return lastSyncAttemptAt; }
    public void setLastSyncAttemptAt(Instant lastSyncAttemptAt) { this.lastSyncAttemptAt = lastSyncAttemptAt; }
}
