package com.payroll.web.notes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.Company;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.CloudEmployeeNoteRepository;
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

import java.time.LocalDate;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class NotesControllerIT {

    private static final LocalDate NOTE_DATE = LocalDate.of(2026, 6, 20);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CloudEmployeeNoteRepository noteRepository;
    @Autowired private AuditLogEntryRepository auditLogEntryRepository;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    @BeforeEach
    void seed() {
        auditLogEntryRepository.deleteAll();
        noteRepository.deleteAll();
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

    private String getUrl(Long companyId, String employeeCode) {
        return "/api/notes/" + companyId + "/" + employeeCode;
    }

    private void postNote(Long companyId, String employeeCode, LocalDate date, String text) throws Exception {
        var body = new NoteRequest(companyId, employeeCode, date, text);
        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // --- auth ---

    @Test
    void postWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-001")))
                .andExpect(status().isUnauthorized());
    }

    // --- POST: create + audit ---

    @Test
    void postCreatesNoteAndWritesAudit() throws Exception {
        var body = new NoteRequest(companyA.getId(), "EMP-001", NOTE_DATE, "Left early for a doctor's appointment");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.text").value("Left early for a doctor's appointment"))
                .andExpect(jsonPath("$.createdBy").value("superadmin"));

        var notes = noteRepository.findByCompanyIdAndEmployeeCodeOrderByCreatedAtDesc(companyA.getId(), "EMP-001");
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getNoteText()).isEqualTo("Left early for a doctor's appointment");

        var audits = auditLogEntryRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getAction()).isEqualTo("NOTE_ADDED");
        assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
        assertThat(audits.get(0).getCompanyId()).isEqualTo(companyA.getId());
    }

    @Test
    void postWithBlankText_returns400_noDbOrAuditWrite() throws Exception {
        var body = new NoteRequest(companyA.getId(), "EMP-002", NOTE_DATE, "   ");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertThat(noteRepository.findByCompanyIdAndEmployeeCodeOrderByCreatedAtDesc(companyA.getId(), "EMP-002"))
                .isEmpty();
        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void postWithMissingText_returns400() throws Exception {
        String bodyJson = "{\"companyId\":" + companyA.getId()
                + ",\"employeeCode\":\"EMP-003\",\"noteDate\":\"" + NOTE_DATE + "\"}";

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isBadRequest());

        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    // --- GET: scoping / ordering ---

    @Test
    void getReturnsNotesScopedToCompanyAndEmployee() throws Exception {
        postNote(companyA.getId(), "EMP-010", NOTE_DATE, "Note for A/EMP-010");
        postNote(companyA.getId(), "EMP-011", NOTE_DATE, "Note for A/EMP-011");
        postNote(companyB.getId(), "EMP-010", NOTE_DATE, "Note for B/EMP-010");

        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-010"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Note for A/EMP-010"));
    }

    @Test
    void getReturnsNotesNewestFirst() throws Exception {
        postNote(companyA.getId(), "EMP-020", NOTE_DATE, "First note");
        postNote(companyA.getId(), "EMP-020", NOTE_DATE.plusDays(1), "Second note");
        postNote(companyA.getId(), "EMP-020", NOTE_DATE.plusDays(2), "Third note");

        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-020"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].text").value("Third note"))
                .andExpect(jsonPath("$[1].text").value("Second note"))
                .andExpect(jsonPath("$[2].text").value("First note"));
    }

    @Test
    void getForUnknownEmployeeReturnsEmptyList() throws Exception {
        mockMvc.perform(get(getUrl(companyA.getId(), "NO-SUCH-EMP"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
