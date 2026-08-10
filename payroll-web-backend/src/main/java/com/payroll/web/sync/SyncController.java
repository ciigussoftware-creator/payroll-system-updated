package com.payroll.web.sync;

import com.payroll.web.security.SyncClientAuthentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final AttendanceSyncService attendanceSyncService;
    private final EmployeeSyncService employeeSyncService;
    private final DayLevelOtSyncService dayLevelOtSyncService;
    private final OtAuthorizationSyncService otAuthorizationSyncService;
    private final WorkingDaysSyncService workingDaysSyncService;

    public SyncController(AttendanceSyncService attendanceSyncService,
                           EmployeeSyncService employeeSyncService,
                           DayLevelOtSyncService dayLevelOtSyncService,
                           OtAuthorizationSyncService otAuthorizationSyncService,
                           WorkingDaysSyncService workingDaysSyncService) {
        this.attendanceSyncService = attendanceSyncService;
        this.employeeSyncService = employeeSyncService;
        this.dayLevelOtSyncService = dayLevelOtSyncService;
        this.otAuthorizationSyncService = otAuthorizationSyncService;
        this.workingDaysSyncService = workingDaysSyncService;
    }

    @PostMapping("/attendance")
    public ResponseEntity<List<AttendanceSyncResult>> syncAttendance(
            @RequestBody List<AttendanceSyncRequest> records,
            Authentication authentication) {
        Long companyId = ((SyncClientAuthentication) authentication).getCompanyId();
        return ResponseEntity.ok(attendanceSyncService.processBatch(companyId, records));
    }

    @PostMapping("/employees")
    public ResponseEntity<List<EmployeeSyncResult>> syncEmployees(
            @RequestBody List<EmployeeSyncRequest> records,
            Authentication authentication) {
        Long companyId = ((SyncClientAuthentication) authentication).getCompanyId();
        return ResponseEntity.ok(employeeSyncService.processBatch(companyId, records));
    }

    @PostMapping("/day-level-ot")
    public ResponseEntity<List<DayLevelOtSyncResult>> syncDayLevelOt(
            @RequestBody List<DayLevelOtSyncRequest> records,
            Authentication authentication) {
        Long companyId = ((SyncClientAuthentication) authentication).getCompanyId();
        return ResponseEntity.ok(dayLevelOtSyncService.processBatch(companyId, records));
    }

    @PostMapping("/ot-authorizations")
    public ResponseEntity<List<OtAuthorizationSyncResult>> syncOtAuthorizations(
            @RequestBody List<OtAuthorizationSyncRequest> records,
            Authentication authentication) {
        Long companyId = ((SyncClientAuthentication) authentication).getCompanyId();
        return ResponseEntity.ok(otAuthorizationSyncService.processBatch(companyId, records));
    }

    @PostMapping("/working-days")
    public ResponseEntity<List<WorkingDaysSyncResult>> syncWorkingDays(
            @RequestBody List<WorkingDaysSyncRequest> records,
            Authentication authentication) {
        Long companyId = ((SyncClientAuthentication) authentication).getCompanyId();
        return ResponseEntity.ok(workingDaysSyncService.processBatch(companyId, records));
    }
}
