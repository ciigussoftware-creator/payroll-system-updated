package com.payroll.web.payrollinput;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payroll-inputs")
public class PayrollInputController {

    private final MonthlyPayrollInputService service;

    public PayrollInputController(MonthlyPayrollInputService service) {
        this.service = service;
    }

    // TODO: once multi-company web login is designed, resolve companyId from the
    // authenticated Super Admin instead of taking it as a query parameter — there is
    // currently only one WebAdminAccount type with no explicit company-to-admin mapping.
    @GetMapping("/{periodMonth}")
    public ResponseEntity<List<MonthlyPayrollInputResponse>> getForMonth(
            @PathVariable("periodMonth") String periodMonth,
            @RequestParam("companyId") Long companyId) {
        return ResponseEntity.ok(service.findByCompanyAndMonth(companyId, periodMonth));
    }

    @PutMapping("/{companyId}/{employeeCode}/{periodMonth}")
    public ResponseEntity<MonthlyPayrollInputResponse> upsert(
            @PathVariable("companyId") Long companyId,
            @PathVariable("employeeCode") String employeeCode,
            @PathVariable("periodMonth") String periodMonth,
            @Valid @RequestBody MonthlyPayrollInputRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.upsert(companyId, employeeCode, periodMonth, request, authentication.getName()));
    }
}
