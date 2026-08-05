package com.payroll.web.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies a batch of desktop attendance records to the cloud store, scoped to the
 * company resolved from the caller's API key. Each record is validated and persisted
 * independently — {@link EmployeeRepository}/{@link AttendanceRecordRepository} calls
 * are each individually transactional (default Spring Data JPA behaviour), so one bad
 * record in a batch cannot roll back or block the others.
 */
@Service
public class AttendanceSyncService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSyncService.class);

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public AttendanceSyncService(EmployeeRepository employeeRepository,
                                  AttendanceRecordRepository attendanceRecordRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    public List<AttendanceSyncResult> processBatch(Long companyId, List<AttendanceSyncRequest> requests) {
        List<AttendanceSyncResult> results = new ArrayList<>(requests.size());
        for (AttendanceSyncRequest request : requests) {
            results.add(processOne(companyId, request));
        }
        return results;
    }

    private AttendanceSyncResult processOne(Long companyId, AttendanceSyncRequest request) {
        try {
            Optional<Employee> employee =
                    employeeRepository.findByEmployeeCodeAndCompanyId(request.employeeCode(), companyId);
            if (employee.isEmpty()) {
                String reason = "Unknown employeeCode '" + request.employeeCode() + "' for this company";
                log.warn("Rejecting sync record syncUuid={}: {}", request.syncUuid(), reason);
                return AttendanceSyncResult.rejected(request.syncUuid(), reason);
            }

            Optional<AttendanceRecord> existing = attendanceRecordRepository.findBySyncUuid(request.syncUuid());
            if (existing.isEmpty()) {
                AttendanceRecord record = new AttendanceRecord();
                record.setSyncUuid(request.syncUuid());
                record.setEmployee(employee.get());
                applyIncoming(record, request);
                record = attendanceRecordRepository.save(record);
                return AttendanceSyncResult.accepted(request.syncUuid(), record.getId());
            }

            AttendanceRecord current = existing.get();
            if (isIdentical(current, request)) {
                return AttendanceSyncResult.noOp(request.syncUuid(), current.getId());
            }

            // A correction: preserve whatever the true original value is before overwriting.
            var originalToStore = request.originalScanDatetime() != null
                    ? request.originalScanDatetime()
                    : (current.getOriginalScanDatetime() != null
                            ? current.getOriginalScanDatetime()
                            : current.getScanDatetime());

            current.setScanDatetime(request.scanDatetime());
            current.setScanType(request.scanType());
            current.setOriginalScanDatetime(originalToStore);
            current.setCorrectionNote(request.correctionNote());
            current.setCorrectedBy(request.correctedBy());
            current = attendanceRecordRepository.save(current);
            return AttendanceSyncResult.updated(request.syncUuid(), current.getId());
        } catch (Exception e) {
            log.warn("Rejecting sync record syncUuid={}: {}", request.syncUuid(), e.getMessage());
            return AttendanceSyncResult.rejected(request.syncUuid(), "Processing error: " + e.getMessage());
        }
    }

    private void applyIncoming(AttendanceRecord record, AttendanceSyncRequest request) {
        record.setScanDatetime(request.scanDatetime());
        record.setScanType(request.scanType());
        record.setOriginalScanDatetime(request.originalScanDatetime());
        record.setCorrectionNote(request.correctionNote());
        record.setCorrectedBy(request.correctedBy());
    }

    private boolean isIdentical(AttendanceRecord current, AttendanceSyncRequest request) {
        return Objects.equals(current.getScanDatetime(), request.scanDatetime())
                && current.getScanType() == request.scanType()
                && Objects.equals(current.getOriginalScanDatetime(), request.originalScanDatetime())
                && Objects.equals(current.getCorrectionNote(), request.correctionNote())
                && Objects.equals(current.getCorrectedBy(), request.correctedBy());
    }
}
