package com.payroll.web.attendance;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final TimestampCorrectionService service;

    public AttendanceController(TimestampCorrectionService service) {
        this.service = service;
    }

    @GetMapping("/{companyId}/{employeeCode}/{date}")
    public ResponseEntity<List<AttendanceRecordResponse>> getForEmployeeAndDate(
            @PathVariable("companyId") Long companyId,
            @PathVariable("employeeCode") String employeeCode,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findForEmployeeAndDate(companyId, employeeCode, date));
    }

    @PutMapping("/{recordId}")
    public ResponseEntity<AttendanceRecordResponse> correct(
            @PathVariable("recordId") Long recordId,
            @Valid @RequestBody TimestampCorrectionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.correct(
                recordId, request.companyId(), request.newScanDatetime(), request.reason(), authentication.getName()));
    }
}
