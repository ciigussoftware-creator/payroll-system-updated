package com.payroll.web.auditlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.Company;
import com.payroll.web.notes.NoteRequest;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.security.JwtService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class AuditLogControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private AuditLogEntryRepository auditLogEntryRepository;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    @BeforeEach
    void seed() {
        auditLogEntryRepository.deleteAll();
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

    private String getUrl(Long companyId) {
        return "/api/audit-log/" + companyId;
    }

    private AuditLogEntry saveAudit(Long companyId, String action, Instant when) {
        AuditLogEntry a = new AuditLogEntry();
        a.setCompanyId(companyId);
        a.setEntryDatetime(when);
        a.setUsername("superadmin");
        a.setAction(action);
        a.setTargetRef("employee=EMP-001,companyId=" + companyId + ",date=2026-06-20");
        return auditLogEntryRepository.save(a);
    }

    // --- auth ---

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(getUrl(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    // --- scoping ---

    @Test
    void getReturnsEntriesScopedToCompany() throws Exception {
        saveAudit(companyA.getId(), "TIMESTAMP_CORRECTED", Instant.parse("2026-06-20T10:00:00Z"));
        saveAudit(companyB.getId(), "TIMESTAMP_CORRECTED", Instant.parse("2026-06-20T10:00:00Z"));

        mockMvc.perform(get(getUrl(companyA.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getOrdersNewestFirst() throws Exception {
        saveAudit(companyA.getId(), "TIMESTAMP_CORRECTED", Instant.parse("2026-06-20T10:00:00Z"));
        saveAudit(companyA.getId(), "NOTE_ADDED", Instant.parse("2026-06-21T10:00:00Z"));
        saveAudit(companyA.getId(), "OT_DAYLEVEL_SET", Instant.parse("2026-06-19T10:00:00Z"));

        mockMvc.perform(get(getUrl(companyA.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("NOTE_ADDED"))
                .andExpect(jsonPath("$[1].action").value("TIMESTAMP_CORRECTED"))
                .andExpect(jsonPath("$[2].action").value("OT_DAYLEVEL_SET"));
    }

    // --- entityType filter ---

    @Test
    void entityTypeFilter_returnsOnlyMatchingEntries() throws Exception {
        saveAudit(companyA.getId(), "TIMESTAMP_CORRECTED", Instant.parse("2026-06-20T10:00:00Z"));
        saveAudit(companyA.getId(), "NOTE_ADDED", Instant.parse("2026-06-20T11:00:00Z"));
        saveAudit(companyA.getId(), "OT_DAYLEVEL_SET", Instant.parse("2026-06-20T12:00:00Z"));

        mockMvc.perform(get(getUrl(companyA.getId()))
                        .param("entityType", "NOTE_ADDED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("NOTE_ADDED"));
    }

    // --- date-range filter, both boundaries ---

    @Test
    void dateRangeFilter_includesEntriesAtBothBoundaryDays() throws Exception {
        LocalDate from = LocalDate.of(2026, 6, 10);
        LocalDate to = LocalDate.of(2026, 6, 12);

        saveAudit(companyA.getId(), "NOTE_ADDED", from.atStartOfDay(ZoneOffset.UTC).toInstant()); // exactly on `from`
        saveAudit(companyA.getId(), "NOTE_ADDED", to.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)); // last moment of `to`
        saveAudit(companyA.getId(), "NOTE_ADDED", from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()); // before range
        saveAudit(companyA.getId(), "NOTE_ADDED", to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()); // after range

        mockMvc.perform(get(getUrl(companyA.getId()))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // --- cross-feature link ---

    @Test
    void noteCreation_appearsInAuditLog() throws Exception {
        var body = new NoteRequest(companyA.getId(), "EMP-050", LocalDate.of(2026, 6, 20), "Cross-feature link test");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get(getUrl(companyA.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("NOTE_ADDED"))
                .andExpect(jsonPath("$[0].newValue").value("Cross-feature link test"));
    }
}
