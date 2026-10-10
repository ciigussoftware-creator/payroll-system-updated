package com.payroll.web.attendance;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.ScanType;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Super Admin's web timestamp-correction power (Phase 6C). Mirrors the desktop's
 * TimestampCorrectionService: mandatory reason, original scan preserved on first
 * correction only, and every correction writes an {@link AuditLogEntry} — reusing the
 * same entity/action naming the desktop proves in TimestampCorrectionIT so both sides
 * of the app produce a single consistent audit trail.
 *
 * <p>MISSING_CLOCK_OUT is never stored — it's derived here the same way
 * {@code SessionBuilder.hasMissingClockOut} derives it in payroll-core: an ENTRY scan
 * with no immediately-following EXIT scan for that day.
 */
@Service
public class TimestampCorrectionService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final Clock clock;

    public TimestampCorrectionService(EmployeeRepository employeeRepository,
                                       AttendanceRecordRepository attendanceRecordRepository,
                                       AuditLogEntryRepository auditLogEntryRepository,
                                       Clock clock) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.clock = clock;
    }

    public List<AttendanceRecordResponse> findForEmployeeAndDate(Long companyId, String employeeCode, LocalDate date) {
        Optional<Employee> employee = employeeRepository.findByEmployeeCodeAndCompanyId(employeeCode, companyId);
        if (employee.isEmpty()) {
            return List.of();
        }
        List<AttendanceRecord> records = recordsForDay(employee.get().getId(), date);
        return toResponses(records);
    }

    @Transactional
    public AttendanceRecordResponse correct(Long recordId, Long companyId, LocalDateTime newScanDatetime,
                                             String reason, String username) {
        AttendanceRecord record = attendanceRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!Objects.equals(record.getEmployee().getCompanyId(), companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        LocalDateTime oldDatetime = record.getScanDatetime();
        if (record.getOriginalScanDatetime() == null) {
            record.setOriginalScanDatetime(oldDatetime);
        }
        record.setScanDatetime(newScanDatetime);
        record.setCorrectionNote(reason);
        attendanceRecordRepository.save(record);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setCompanyId(companyId);
        audit.setEntryDatetime(clock.instant());
        audit.setUsername(username);
        audit.setAction("TIMESTAMP_CORRECTED");
        audit.setTargetRef("employee=" + record.getEmployee().getEmployeeCode()
                + ",date=" + newScanDatetime.toLocalDate());
        audit.setOldValue(oldDatetime + "/" + record.getScanType());
        audit.setNewValue(newScanDatetime + "/" + record.getScanType());
        audit.setReason(reason);
        auditLogEntryRepository.save(audit);

        List<AttendanceRecord> dayRecords = recordsForDay(record.getEmployee().getId(), newScanDatetime.toLocalDate());
        boolean missingClockOut = missingClockOutRecordIds(dayRecords).contains(record.getId());
        return new AttendanceRecordResponse(record.getId(), record.getScanDatetime(), record.getScanType(), missingClockOut);
    }

    private List<AttendanceRecord> recordsForDay(Long employeeId, LocalDate date) {
        return attendanceRecordRepository.findByEmployeeIdAndScanDatetimeRange(
                employeeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    private List<AttendanceRecordResponse> toResponses(List<AttendanceRecord> records) {
        Set<Long> missingClockOutIds = missingClockOutRecordIds(records);
        return records.stream()
                .map(r -> new AttendanceRecordResponse(r.getId(), r.getScanDatetime(), r.getScanType(),
                        missingClockOutIds.contains(r.getId())))
                .toList();
    }

    /**
     * Same walk as {@code SessionBuilder.buildSessions}: an ENTRY paired with the very
     * next scan being an EXIT closes a session; any other ENTRY is left dangling and
     * flagged. Records must already be sorted by scanDatetime (the repository query
     * guarantees this).
     */
    private Set<Long> missingClockOutRecordIds(List<AttendanceRecord> records) {
        Set<Long> ids = new HashSet<>();
        int i = 0;
        while (i < records.size()) {
            AttendanceRecord current = records.get(i);
            if (current.getScanType() == ScanType.ENTRY) {
                int next = i + 1;
                if (next < records.size() && records.get(next).getScanType() == ScanType.EXIT) {
                    i = next + 1;
                } else {
                    ids.add(current.getId());
                    i++;
                }
            } else {
                i++;
            }
        }
        return ids;
    }
}
