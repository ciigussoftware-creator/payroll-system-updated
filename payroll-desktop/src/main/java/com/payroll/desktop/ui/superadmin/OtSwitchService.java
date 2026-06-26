package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.DayType;
import com.payroll.core.entity.OtEmployeeAuthorization;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class OtSwitchService {

    private final DayLevelOTConfigRepository dayLevelOTRepo;
    private final OtEmployeeAuthorizationRepository otAuthRepo;
    private final AuditLogRepository auditLogRepo;

    public OtSwitchService(DayLevelOTConfigRepository dayLevelOTRepo,
                            OtEmployeeAuthorizationRepository otAuthRepo,
                            AuditLogRepository auditLogRepo) {
        this.dayLevelOTRepo = dayLevelOTRepo;
        this.otAuthRepo = otAuthRepo;
        this.auditLogRepo = auditLogRepo;
    }

    public Optional<DayLevelOTConfig> loadDayConfig(LocalDate date) {
        return dayLevelOTRepo.findByDate(date);
    }

    public List<OtEmployeeAuthorization> loadEmployeeAuthorizations(LocalDate date) {
        return otAuthRepo.findByDate(date);
    }

    /**
     * Upserts the day-level OT configuration for a date and writes an audit log entry.
     * Retroactive changes (past dates) require a non-blank reason.
     */
    public void saveDayLevel(LocalDate date, DayType dayType, boolean isAllStaffOt,
                              String username, String reason) {
        if (date.isBefore(LocalDate.now()) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Retroactive changes require a reason");
        }

        var existing = dayLevelOTRepo.findByDate(date);
        String oldValue = existing.map(c -> c.getDayType() + "/" + c.isAllStaffOt()).orElse(null);
        String newValue = dayType + "/" + isAllStaffOt;

        DayLevelOTConfig config = existing.orElseGet(DayLevelOTConfig::new);
        config.setConfigDate(date);
        config.setDayType(dayType);
        config.setAllStaffOt(isAllStaffOt);
        config.setSetBy(0L);
        config.setSetAt(Instant.now());

        if (existing.isPresent()) {
            dayLevelOTRepo.update(config);
        } else {
            dayLevelOTRepo.save(config);
        }

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(username);
        audit.setAction("OT_DAYLEVEL_SET");
        audit.setTargetRef("date=" + date);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setReason(blankToNull(reason));
        auditLogRepo.save(audit);
    }

    /**
     * Saves per-employee OT authorization for a date and writes an audit log entry.
     * Only writes if the authorization value changed. Retroactive changes require a reason.
     */
    public void saveEmployeeAuthorization(LocalDate date, Long employeeId, boolean newAuth,
                                          String employeeRef, String username, String reason) {
        if (date.isBefore(LocalDate.now()) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Retroactive changes require a reason");
        }

        var existing = otAuthRepo.findByEmployeeAndDate(employeeId, date);
        boolean currentAuth = existing.map(OtEmployeeAuthorization::isAuthorized).orElse(false);
        if (currentAuth == newAuth) return;

        otAuthRepo.upsert(employeeId, date, newAuth, username);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(username);
        audit.setAction("OT_EMPLOYEE_SET");
        audit.setTargetRef("employee=" + employeeRef + " date=" + date);
        audit.setOldValue(String.valueOf(currentAuth));
        audit.setNewValue(String.valueOf(newAuth));
        audit.setReason(blankToNull(reason));
        auditLogRepo.save(audit);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
