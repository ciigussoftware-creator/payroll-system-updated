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

    public SyncController(AttendanceSyncService attendanceSyncService) {
        this.attendanceSyncService = attendanceSyncService;
    }

    @PostMapping("/attendance")
    public ResponseEntity<List<AttendanceSyncResult>> syncAttendance(
            @RequestBody List<AttendanceSyncRequest> records,
            Authentication authentication) {
        Long companyId = ((SyncClientAuthentication) authentication).getCompanyId();
        return ResponseEntity.ok(attendanceSyncService.processBatch(companyId, records));
    }
}
