package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    public AttendanceRecord() {}

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
}
