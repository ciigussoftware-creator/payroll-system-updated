package com.payroll.web.company;

import com.payroll.core.entity.Company;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.security.JwtService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class CompanyControllerIT {

    private static final String LIST_URL = "/api/companies";

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JwtService jwtService;

    private String token;

    @BeforeEach
    void seed() {
        companyRepository.deleteAll();

        saveCompany("Wood Lanka", "WL");
        saveCompany("DCH Plywood", "DCH");

        token = jwtService.generateToken("superadmin");
    }

    private Company saveCompany(String name, String code) {
        Company c = new Company();
        c.setName(name);
        c.setCode(code);
        return companyRepository.save(c);
    }

    @Test
    void listReturnsAllCompaniesWithIdAndName() throws Exception {
        mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("Wood Lanka", "DCH Plywood")))
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void getWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
