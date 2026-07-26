package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.DayType;
import com.payroll.core.entity.OtEmployeeAuthorization;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;

import java.time.DayOfWeek;
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

    /** Desired OT-authorized state for one employee, used by {@link #saveAll}. */
    public record EmployeeAuth(Long employeeId, String employeeCode, boolean authorized) {
    }

    /**
     * Derives DayType from the calendar (Sunday vs. weekday) — the OT Switch screen no longer
     * lets an admin pick this manually, but the field and its meaning to the statutory/attendance
     * engine are unchanged.
     */
    public static DayType deriveDayType(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? DayType.SUNDAY : DayType.WEEKDAY;
    }

    /**
     * Saves the day-level All-Staff-OT flag and every listed employee's authorization for a
     * date in one operation — the screen now has a single "Save" button instead of two.
     * Retroactive changes require a reason, checked once up front so the whole save either
     * succeeds or fails together (no partial writes).
     *
     * <p>When {@code isHoliday} is true, dayType is stored as MERCANTILE_HOLIDAY so the
     * attendance engine treats the day like a Sunday (all worked time is OT, zero day credit).
     * Otherwise dayType falls back to {@link #deriveDayType(LocalDate)} as before.
     */
    public void saveAll(LocalDate date, boolean isAllStaffOt, boolean isHoliday,
                        List<EmployeeAuth> employeeAuthorizations,
                        String username, String reason) {
        if (date.isBefore(LocalDate.now()) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Retroactive changes require a reason");
        }

        DayType dayType = isHoliday ? DayType.MERCANTILE_HOLIDAY : deriveDayType(date);
        saveDayLevel(date, dayType, isAllStaffOt, username, reason);
        for (EmployeeAuth auth : employeeAuthorizations) {
            saveEmployeeAuthorization(date, auth.employeeId(), auth.authorized(),
                    auth.employeeCode(), username, reason);
        }
    }
}
