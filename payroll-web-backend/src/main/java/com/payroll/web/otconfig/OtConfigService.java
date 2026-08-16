package com.payroll.web.otconfig;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.CloudDayLevelOTConfig;
import com.payroll.core.entity.CloudOtEmployeeAuthorization;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
import com.payroll.web.sync.DayLevelOtSyncRequest;
import com.payroll.web.sync.DayLevelOtSyncService;
import com.payroll.web.sync.OtAuthorizationSyncRequest;
import com.payroll.web.sync.OtAuthorizationSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Super Admin's web write access to the two OT switches (Phase 6D), built directly on top of
 * the Phase 5C-1 sync-receiver's upsert logic (via {@link DayLevelOtSyncService#upsert} and
 * {@link OtAuthorizationSyncService#upsert}) so there is exactly one place that knows how to
 * correctly upsert {@link CloudDayLevelOTConfig} and {@link CloudOtEmployeeAuthorization}.
 *
 * <p>Mirrors the desktop's {@code OtSwitchService}: a reason is required only for retroactive
 * (past-date) changes, and every successful write records an {@link AuditLogEntry} — the same
 * mechanism proved in {@code OtSwitchIT} and reused by 6C's TimestampCorrectionService, so both
 * switches land in a single consistent audit trail alongside timestamp corrections.
 */
@Service
public class OtConfigService {

    private final CloudDayLevelOTConfigRepository dayLevelRepository;
    private final CloudOtEmployeeAuthorizationRepository employeeAuthRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final DayLevelOtSyncService dayLevelOtSyncService;
    private final OtAuthorizationSyncService otAuthorizationSyncService;
    private final Clock clock;

    public OtConfigService(CloudDayLevelOTConfigRepository dayLevelRepository,
                            CloudOtEmployeeAuthorizationRepository employeeAuthRepository,
                            AuditLogEntryRepository auditLogEntryRepository,
                            DayLevelOtSyncService dayLevelOtSyncService,
                            OtAuthorizationSyncService otAuthorizationSyncService,
                            Clock clock) {
        this.dayLevelRepository = dayLevelRepository;
        this.employeeAuthRepository = employeeAuthRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.dayLevelOtSyncService = dayLevelOtSyncService;
        this.otAuthorizationSyncService = otAuthorizationSyncService;
        this.clock = clock;
    }

    public DayLevelOtStateResponse getDayLevelState(Long companyId, LocalDate date) {
        return dayLevelRepository.findByCompanyIdAndConfigDate(companyId, date)
                .map(DayLevelOtStateResponse::from)
                .orElseGet(() -> DayLevelOtStateResponse.derived(date));
    }

    @Transactional
    public DayLevelOtStateResponse setDayLevelState(Long companyId, LocalDate date,
                                                      DayLevelOtUpdateRequest request, String username) {
        if (request.dayType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayType is required");
        }
        validateReason(date, request.reason());

        Optional<CloudDayLevelOTConfig> existing = dayLevelRepository.findByCompanyIdAndConfigDate(companyId, date);
        String oldValue = existing.map(c -> c.getDayType() + "/" + c.isAllStaffOt()).orElse(null);

        DayLevelOtSyncService.UpsertOutcome outcome = dayLevelOtSyncService.upsert(companyId,
                new DayLevelOtSyncRequest(date, request.isAllStaffOt(), request.dayType(), null, clock.instant()));

        String newValue = outcome.config().getDayType() + "/" + outcome.config().isAllStaffOt();

        AuditLogEntry audit = new AuditLogEntry();
        audit.setCompanyId(companyId);
        audit.setEntryDatetime(clock.instant());
        audit.setUsername(username);
        audit.setAction("OT_DAYLEVEL_SET");
        audit.setTargetRef("companyId=" + companyId + ",date=" + date);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setReason(blankToNull(request.reason()));
        auditLogEntryRepository.save(audit);

        return DayLevelOtStateResponse.from(outcome.config());
    }

    public List<EmployeeAuthorizationResponse> getEmployeeAuthorizations(Long companyId, LocalDate date) {
        return employeeAuthRepository.findByCompanyIdAndAuthDate(companyId, date).stream()
                .map(EmployeeAuthorizationResponse::from)
                .toList();
    }

    @Transactional
    public EmployeeAuthorizationResponse setEmployeeAuthorization(Long companyId, String employeeCode, LocalDate date,
                                                                    EmployeeAuthorizationUpdateRequest request,
                                                                    String username) {
        validateReason(date, request.reason());

        Optional<CloudOtEmployeeAuthorization> existing =
                employeeAuthRepository.findByCompanyIdAndEmployeeCodeAndAuthDate(companyId, employeeCode, date);
        boolean oldAuthorized = existing.map(CloudOtEmployeeAuthorization::isAuthorized).orElse(false);

        OtAuthorizationSyncService.UpsertOutcome outcome = otAuthorizationSyncService.upsert(companyId,
                new OtAuthorizationSyncRequest(employeeCode, date, request.authorized(), username, clock.instant()));

        AuditLogEntry audit = new AuditLogEntry();
        audit.setCompanyId(companyId);
        audit.setEntryDatetime(clock.instant());
        audit.setUsername(username);
        audit.setAction("OT_EMPLOYEE_SET");
        audit.setTargetRef("employee=" + employeeCode + ",companyId=" + companyId + ",date=" + date);
        audit.setOldValue(String.valueOf(oldAuthorized));
        audit.setNewValue(String.valueOf(request.authorized()));
        audit.setReason(blankToNull(request.reason()));
        auditLogEntryRepository.save(audit);

        return EmployeeAuthorizationResponse.from(outcome.config());
    }

    private void validateReason(LocalDate date, String reason) {
        if (date.isBefore(LocalDate.now(clock)) && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Retroactive changes require a reason");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
