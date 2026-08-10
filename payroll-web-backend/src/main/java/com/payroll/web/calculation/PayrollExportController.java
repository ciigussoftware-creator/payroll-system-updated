package com.payroll.web.calculation;

import com.payroll.core.entity.Company;
import com.payroll.web.repository.CompanyRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/payroll/export")
public class PayrollExportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PayrollCalculationService calculationService;
    private final PayrollExcelExportService excelExportService;
    private final CompanyRepository companyRepository;

    public PayrollExportController(
            PayrollCalculationService calculationService,
            PayrollExcelExportService excelExportService,
            CompanyRepository companyRepository) {
        this.calculationService = calculationService;
        this.excelExportService = excelExportService;
        this.companyRepository = companyRepository;
    }

    @GetMapping("/{periodMonth}")
    public ResponseEntity<byte[]> export(
            @PathVariable("periodMonth") String periodMonth,
            @RequestParam("companyId") Long companyId) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId));

        List<PayrollCalculationResponse> rows = calculationService.calculateForMonth(companyId, periodMonth);
        byte[] workbook = excelExportService.export(company.getName(), periodMonth, rows);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(excelExportService.fileName(company.getName(), periodMonth), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(workbook);
    }
}
