package com.payroll.web.otconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.Company;
import com.payroll.core.entity.DayType;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class OtConfigControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CloudDayLevelOTConfigRepository dayLevelRepository;
    @Autowired private CloudOtEmployeeAuthorizationRepository employeeAuthRepository;
    @Autowired private AuditLogEntryRepository auditLogEntryRepository;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    private static LocalDate nextSunday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private static LocalDate nextWeekday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private static final LocalDate FUTURE = LocalDate.now().plusDays(2);
    private static final LocalDate PAST = LocalDate.now().minusDays(1);

    @BeforeEach
    void seed() {
        auditLogEntryRepository.deleteAll();
        employeeAuthRepository.deleteAll();
        dayLevelRepository.deleteAll();
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

    private String dayLevelUrl(Long companyId, LocalDate date) {
        return "/api/ot-config/day-level/" + companyId + "/" + date;
    }

    private String employeeAuthListUrl(Long companyId, LocalDate date) {
        return "/api/ot-config/employee-authorizations/" + companyId + "/" + date;
    }

    private String employeeAuthUrl(Long companyId, String employeeCode, LocalDate date) {
        return "/api/ot-config/employee-authorization/" + companyId + "/" + employeeCode + "/" + date;
    }

    // --- auth: 401 without a valid token on all four endpoints ---

    @Test
    void getDayLevelWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(dayLevelUrl(companyA.getId(), FUTURE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putDayLevelWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put(dayLevelUrl(companyA.getId(), FUTURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEmployeeAuthorizationsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(employeeAuthListUrl(companyA.getId(), FUTURE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putEmployeeAuthorizationWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put(employeeAuthUrl(companyA.getId(), "EMP-001", FUTURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- GET day-level: derived defaults ---

    @Test
    void getDayLevelForSundayWithNoConfig_derivesAllStaffOtOn() throws Exception {
        LocalDate sunday = nextSunday();

        mockMvc.perform(get(dayLevelUrl(companyA.getId(), sunday))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAllStaffOt").value(true))
                .andExpect(jsonPath("$.dayType").value("SUNDAY"))
                .andExpect(jsonPath("$.setAt").value(nullValue()));
    }

    @Test
    void getDayLevelForWeekdayWithNoConfig_derivesAllStaffOtOff() throws Exception {
        LocalDate weekday = nextWeekday();

        mockMvc.perform(get(dayLevelUrl(companyA.getId(), weekday))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAllStaffOt").value(false))
                .andExpect(jsonPath("$.dayType").value("WEEKDAY"));
    }

    // --- PUT day-level: update + audit ---

    @Test
    void putDayLevel_updatesConfigAndWritesAudit() throws Exception {
        var body = new DayLevelOtUpdateRequest(true, DayType.WEEKDAY, null);

        mockMvc.perform(put(dayLevelUrl(companyA.getId(), FUTURE))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAllStaffOt").value(true))
                .andExpect(jsonPath("$.dayType").value("WEEKDAY"));

        var saved = dayLevelRepository.findByCompanyIdAndConfigDate(companyA.getId(), FUTURE).orElseThrow();
        assertThat(saved.isAllStaffOt()).isTrue();
        assertThat(saved.getDayType()).isEqualTo(DayType.WEEKDAY);

        var audits = auditLogEntryRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getAction()).isEqualTo("OT_DAYLEVEL_SET");
        assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
        assertThat(audits.get(0).getOldValue()).isNull();
        assertThat(audits.get(0).getNewValue()).isEqualTo("WEEKDAY/true");
    }

    @Test
    void putDayLevel_scopedToCompany() throws Exception {
        var bodyA = new DayLevelOtUpdateRequest(true, DayType.WEEKDAY, null);
        var bodyB = new DayLevelOtUpdateRequest(false, DayType.WEEKDAY, null);

        mockMvc.perform(put(dayLevelUrl(companyA.getId(), FUTURE))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyA)))
                .andExpect(status().isOk());

        mockMvc.perform(put(dayLevelUrl(companyB.getId(), FUTURE))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyB)))
                .andExpect(status().isOk());

        assertThat(dayLevelRepository.findByCompanyIdAndConfigDate(companyA.getId(), FUTURE).orElseThrow()
                .isAllStaffOt()).isTrue();
        assertThat(dayLevelRepository.findByCompanyIdAndConfigDate(companyB.getId(), FUTURE).orElseThrow()
                .isAllStaffOt()).isFalse();
    }

    @Test
    void putDayLevel_retroactiveWithBlankReason_returns400_noDbOrAuditWrite() throws Exception {
        var body = new DayLevelOtUpdateRequest(true, DayType.WEEKDAY, "");

        mockMvc.perform(put(dayLevelUrl(companyA.getId(), PAST))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertThat(dayLevelRepository.findByCompanyIdAndConfigDate(companyA.getId(), PAST)).isEmpty();
        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void putDayLevel_retroactiveWithMissingReason_returns400() throws Exception {
        String bodyJson = "{\"isAllStaffOt\":true,\"dayType\":\"WEEKDAY\"}";

        mockMvc.perform(put(dayLevelUrl(companyA.getId(), PAST))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isBadRequest());

        assertThat(dayLevelRepository.findByCompanyIdAndConfigDate(companyA.getId(), PAST)).isEmpty();
        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void putDayLevel_retroactiveWithReason_savedAndReasonInAudit() throws Exception {
        var body = new DayLevelOtUpdateRequest(true, DayType.MERCANTILE_HOLIDAY, "Correction approved by manager");

        mockMvc.perform(put(dayLevelUrl(companyA.getId(), PAST))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(dayLevelRepository.findByCompanyIdAndConfigDate(companyA.getId(), PAST)).isPresent();
        var audits = auditLogEntryRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getReason()).isEqualTo("Correction approved by manager");
    }

    // --- GET employee-authorizations: scoping ---

    @Test
    void getEmployeeAuthorizations_returnsListScopedToCompany() throws Exception {
        putEmployeeAuth(companyA.getId(), "EMP-001", FUTURE, true, null);
        putEmployeeAuth(companyA.getId(), "EMP-002", FUTURE, false, null);
        putEmployeeAuth(companyB.getId(), "EMP-001", FUTURE, true, null);

        mockMvc.perform(get(employeeAuthListUrl(companyA.getId(), FUTURE))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getEmployeeAuthorizations_emptyWhenNoneSet() throws Exception {
        mockMvc.perform(get(employeeAuthListUrl(companyA.getId(), FUTURE))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- PUT employee-authorization: update + audit ---

    @Test
    void putEmployeeAuthorization_updatesRowAndWritesAudit() throws Exception {
        var body = new EmployeeAuthorizationUpdateRequest(true, null);

        mockMvc.perform(put(employeeAuthUrl(companyA.getId(), "EMP-010", FUTURE))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorized").value(true))
                .andExpect(jsonPath("$.employeeCode").value("EMP-010"));

        var saved = employeeAuthRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-010", FUTURE).orElseThrow();
        assertThat(saved.isAuthorized()).isTrue();

        var audits = auditLogEntryRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getAction()).isEqualTo("OT_EMPLOYEE_SET");
        assertThat(audits.get(0).getOldValue()).isEqualTo("false");
        assertThat(audits.get(0).getNewValue()).isEqualTo("true");
    }

    @Test
    void putEmployeeAuthorization_allowsUnknownEmployeeCode() throws Exception {
        var body = new EmployeeAuthorizationUpdateRequest(true, null);

        mockMvc.perform(put(employeeAuthUrl(companyA.getId(), "MADE-UP-CODE", FUTURE))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(employeeAuthRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "MADE-UP-CODE", FUTURE)).isPresent();
    }

    @Test
    void putEmployeeAuthorization_retroactiveWithBlankReason_returns400_noDbOrAuditWrite() throws Exception {
        var body = new EmployeeAuthorizationUpdateRequest(true, "");

        mockMvc.perform(put(employeeAuthUrl(companyA.getId(), "EMP-020", PAST))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertThat(employeeAuthRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-020", PAST)).isEmpty();
        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void putEmployeeAuthorization_retroactiveWithReason_savedAndReasonInAudit() throws Exception {
        var body = new EmployeeAuthorizationUpdateRequest(true, "OT approved retroactively");

        mockMvc.perform(put(employeeAuthUrl(companyA.getId(), "EMP-030", PAST))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(employeeAuthRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-030", PAST)).isPresent();
        var audits = auditLogEntryRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getReason()).isEqualTo("OT approved retroactively");
    }

    // --- two-switch semantics: independence of per-employee rows ---

    @Test
    void puttingOneEmployeeAuthorization_doesNotAffectAnotherEmployeeSameDate() throws Exception {
        putEmployeeAuth(companyA.getId(), "EMP-041", FUTURE, false, null);
        putEmployeeAuth(companyA.getId(), "EMP-042", FUTURE, false, null);

        // Flip only EMP-041 on.
        putEmployeeAuth(companyA.getId(), "EMP-041", FUTURE, true, null);

        var emp041 = employeeAuthRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-041", FUTURE).orElseThrow();
        var emp042 = employeeAuthRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-042", FUTURE).orElseThrow();

        assertThat(emp041.isAuthorized()).isTrue();
        assertThat(emp042.isAuthorized()).isFalse();
    }

    private void putEmployeeAuth(Long companyId, String employeeCode, LocalDate date,
                                  boolean authorized, String reason) throws Exception {
        var body = new EmployeeAuthorizationUpdateRequest(authorized, reason);
        mockMvc.perform(put(employeeAuthUrl(companyId, employeeCode, date))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
