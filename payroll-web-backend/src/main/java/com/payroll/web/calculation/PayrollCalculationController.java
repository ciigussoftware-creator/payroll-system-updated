package com.payroll.web.calculation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payroll/calculate")
public class PayrollCalculationController {

    private final PayrollCalculationService service;

    public PayrollCalculationController(PayrollCalculationService service) {
        this.service = service;
    }

    // TODO: once multi-company web login is designed, resolve companyId from the
    // authenticated Super Admin instead of taking it as a query parameter — there is
    // currently only one WebAdminAccount type with no explicit company-to-admin mapping.
    @GetMapping("/{periodMonth}")
    public ResponseEntity<List<PayrollCalculationResponse>> calculate(
            @PathVariable("periodMonth") String periodMonth,
            @RequestParam("companyId") Long companyId) {
        return ResponseEntity.ok(service.calculateForMonth(companyId, periodMonth));
    }
}
