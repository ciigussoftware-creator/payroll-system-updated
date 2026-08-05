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

    public SyncController(AttendanceSyncService attendanceSyncService, EmployeeSyncService employeeSyncService) {
        this.attendanceSyncService = attendanceSyncService;
        this.employeeSyncService = employeeSyncService;
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
}
