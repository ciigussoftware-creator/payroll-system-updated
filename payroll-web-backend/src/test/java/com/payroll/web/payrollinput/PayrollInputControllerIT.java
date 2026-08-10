package com.payroll.web.payrollinput;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.Company;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.MonthlyPayrollInputRepository;
import com.payroll.web.security.JwtService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class PayrollInputControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MonthlyPayrollInputRepository monthlyPayrollInputRepository;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    @BeforeEach
    void seed() {
        monthlyPayrollInputRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Wood Lanka", "WL");
        companyB = saveCompany("DCH Plywood", "DCH");

        token = jwtService.generateToken("superadmin");
    }

    private Company saveCompany(String name, String code) {
        Company c = new Company();
        c.setName(name);
        c.setCode(code);
        return companyRepository.save(c);
    }

    private String putUrl(Long companyId, String employeeCode, String periodMonth) {
        return "/api/payroll-inputs/" + companyId + "/" + employeeCode + "/" + periodMonth;
    }

    // --- auth ---

    @Test
    void putWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-001", "2026-08"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-001", "2026-08"))
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/payroll-inputs/2026-08").param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/payroll-inputs/2026-08")
                        .header("Authorization", "Bearer not-a-real-token")
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    // --- upsert: insert / update ---

    @Test
    void putCreatesNewEntryWhenAbsent() throws Exception {
        var body = new MonthlyPayrollInputRequest(
                new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("250.00"));

        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-001", "2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.periodMonth").value("2026-08"))
                .andExpect(jsonPath("$.allowance").value(1000.00))
                .andExpect(jsonPath("$.salaryAdvance").value(500.00))
                .andExpect(jsonPath("$.festivalAdvance").value(250.00))
                .andExpect(jsonPath("$.updatedBy").value("superadmin"));

        var saved = monthlyPayrollInputRepository
                .findByCompanyIdAndEmployeeCodeAndPeriodMonth(companyA.getId(), "EMP-001", "2026-08")
                .orElseThrow();
        assertThat(saved.getAllowance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void putDefaultsOmittedAmountsToZero() throws Exception {
        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-002", "2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowance").value(0))
                .andExpect(jsonPath("$.salaryAdvance").value(0))
                .andExpect(jsonPath("$.festivalAdvance").value(0));
    }

    @Test
    void putUpdatesExistingEntryRatherThanDuplicating() throws Exception {
        var initial = new MonthlyPayrollInputRequest(
                new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("250.00"));
        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-001", "2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initial)))
                .andExpect(status().isOk());

        var updated = new MonthlyPayrollInputRequest(
                new BigDecimal("1200.00"), new BigDecimal("600.00"), new BigDecimal("300.00"));
        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-001", "2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowance").value(1200.00));

        assertThat(monthlyPayrollInputRepository.findAll()).hasSize(1);
        var saved = monthlyPayrollInputRepository
                .findByCompanyIdAndEmployeeCodeAndPeriodMonth(companyA.getId(), "EMP-001", "2026-08")
                .orElseThrow();
        assertThat(saved.getAllowance()).isEqualByComparingTo("1200.00");
        assertThat(saved.getSalaryAdvance()).isEqualByComparingTo("600.00");
        assertThat(saved.getFestivalAdvance()).isEqualByComparingTo("300.00");
    }

    // --- validation ---

    @Test
    void putWithNegativeAmountReturns400() throws Exception {
        var body = new MonthlyPayrollInputRequest(
                new BigDecimal("-1.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        mockMvc.perform(put(putUrl(companyA.getId(), "EMP-001", "2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertThat(monthlyPayrollInputRepository
                .findByCompanyIdAndEmployeeCodeAndPeriodMonth(companyA.getId(), "EMP-001", "2026-08"))
                .isEmpty();
    }

    // --- GET: scoping / segregation ---

    @Test
    void getReturnsAllEntriesForMonthScopedToCompany() throws Exception {
        putEntry(companyA.getId(), "EMP-001", "2026-08", "1000.00", "0", "0");
        putEntry(companyA.getId(), "EMP-002", "2026-08", "2000.00", "0", "0");
        putEntry(companyA.getId(), "EMP-001", "2026-07", "999.00", "0", "0");

        mockMvc.perform(get("/api/payroll-inputs/2026-08")
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void twoCompaniesSameEmployeeCodeAndMonthStaySegregated() throws Exception {
        putEntry(companyA.getId(), "EMP-001", "2026-08", "1000.00", "0", "0");
        putEntry(companyB.getId(), "EMP-001", "2026-08", "5000.00", "0", "0");

        mockMvc.perform(get("/api/payroll-inputs/2026-08")
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].allowance").value(1000.00));

        mockMvc.perform(get("/api/payroll-inputs/2026-08")
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].allowance").value(5000.00));

        var savedA = monthlyPayrollInputRepository
                .findByCompanyIdAndEmployeeCodeAndPeriodMonth(companyA.getId(), "EMP-001", "2026-08").orElseThrow();
        var savedB = monthlyPayrollInputRepository
                .findByCompanyIdAndEmployeeCodeAndPeriodMonth(companyB.getId(), "EMP-001", "2026-08").orElseThrow();
        assertThat(savedA.getId()).isNotEqualTo(savedB.getId());
    }

    private void putEntry(Long companyId, String employeeCode, String periodMonth,
                           String allowance, String salaryAdvance, String festivalAdvance) throws Exception {
        var body = new MonthlyPayrollInputRequest(
                new BigDecimal(allowance), new BigDecimal(salaryAdvance), new BigDecimal(festivalAdvance));
        mockMvc.perform(put(putUrl(companyId, employeeCode, periodMonth))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
