package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;

import java.time.Instant;
import java.time.LocalDateTime;

public class TimestampCorrectionService {

    private final AttendanceRecordRepository attendanceRepo;
    private final AuditLogRepository auditLogRepo;

    public TimestampCorrectionService(AttendanceRecordRepository attendanceRepo,
                                      AuditLogRepository auditLogRepo) {
        this.attendanceRepo = attendanceRepo;
        this.auditLogRepo = auditLogRepo;
    }

    /**
     * Corrects the scanDatetime and/or scanType of an existing scan record.
     * The first correction preserves originalScanDatetime; subsequent corrections leave it intact
     * so the true original is never overwritten.
     */
    public void correctScan(Long recordId, LocalDateTime newDatetime, ScanType newScanType,
                             String reason, String username) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for timestamp corrections");
        }

        AttendanceRecord record = attendanceRepo.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance record not found: " + recordId));

        LocalDateTime oldDatetime = record.getScanDatetime();
        ScanType oldType = record.getScanType();

        // Preserve the true original only on first correction
        LocalDateTime originalToPreserve = record.getOriginalScanDatetime() == null ? oldDatetime : null;

        attendanceRepo.applyCorrection(recordId, newDatetime, newScanType, originalToPreserve, reason);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(username);
        audit.setAction("TIMESTAMP_CORRECTED");
        audit.setTargetRef("employee=" + record.getEmployee().getEmployeeCode()
                + ",date=" + newDatetime.toLocalDate());
        audit.setOldValue(oldDatetime + "/" + oldType);
        audit.setNewValue(newDatetime + "/" + newScanType);
        audit.setReason(reason);
        auditLogRepo.save(audit);
    }

    /**
     * Adds a manually entered scan for an employee. The new record is immediately
     * eligible for sync (syncedToCloud defaults to false).
     *
     * TODO: cloud conflict — adding a scan for a date that is already closed on the cloud
     * side may create ordering or attendance-engine inconsistencies. Flag for review
     * before enabling cloud sync for manually added scans.
     */
    public AttendanceRecord addMissingScan(Employee employee, LocalDateTime scanTime,
                                           ScanType scanType, String reason, String username) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when adding a scan");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setScanDatetime(scanTime);
        record.setScanType(scanType);
        record.setCorrectionNote(reason);
        // originalScanDatetime intentionally null: there was no original scan for this slot
        // correctedBy (Long) left null: no Long user ID available; username is in AuditLogEntry

        AttendanceRecord saved = attendanceRepo.save(record);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(username);
        audit.setAction("SCAN_ADDED");
        audit.setTargetRef("employee=" + employee.getEmployeeCode()
                + ",date=" + scanTime.toLocalDate());
        audit.setNewValue(scanTime + "/" + scanType);
        audit.setReason(reason);
        auditLogRepo.save(audit);

        return saved;
    }
}
