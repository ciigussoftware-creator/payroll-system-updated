package com.payroll.web.otconfig;

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
@RequestMapping("/api/ot-config")
public class OtConfigController {

    private final OtConfigService service;

    public OtConfigController(OtConfigService service) {
        this.service = service;
    }

    @GetMapping("/day-level/{companyId}/{date}")
    public ResponseEntity<DayLevelOtStateResponse> getDayLevel(
            @PathVariable("companyId") Long companyId,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getDayLevelState(companyId, date));
    }

    @PutMapping("/day-level/{companyId}/{date}")
    public ResponseEntity<DayLevelOtStateResponse> putDayLevel(
            @PathVariable("companyId") Long companyId,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody DayLevelOtUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.setDayLevelState(companyId, date, request, authentication.getName()));
    }

    @GetMapping("/employee-authorizations/{companyId}/{date}")
    public ResponseEntity<List<EmployeeAuthorizationResponse>> getEmployeeAuthorizations(
            @PathVariable("companyId") Long companyId,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getEmployeeAuthorizations(companyId, date));
    }

    @PutMapping("/employee-authorization/{companyId}/{employeeCode}/{date}")
    public ResponseEntity<EmployeeAuthorizationResponse> putEmployeeAuthorization(
            @PathVariable("companyId") Long companyId,
            @PathVariable("employeeCode") String employeeCode,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody EmployeeAuthorizationUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.setEmployeeAuthorization(companyId, employeeCode, date, request, authentication.getName()));
    }
}
