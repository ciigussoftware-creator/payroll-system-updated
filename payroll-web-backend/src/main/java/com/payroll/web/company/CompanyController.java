package com.payroll.web.company;

import com.payroll.web.repository.CompanyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping
    public ResponseEntity<List<CompanyResponse>> list() {
        List<CompanyResponse> companies = companyRepository.findAll().stream()
                .map(c -> new CompanyResponse(c.getId(), c.getName()))
                .toList();
        return ResponseEntity.ok(companies);
    }
}
