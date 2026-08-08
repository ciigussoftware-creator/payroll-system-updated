package com.payroll.web.calculation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.CloudDayLevelOTConfig;
import com.payroll.core.entity.CloudOtEmployeeAuthorization;
import com.payroll.core.entity.CloudWorkingDaysConfig;
import com.payroll.core.entity.Company;
import com.payroll.core.entity.DayType;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.MonthlyPayrollInput;
import com.payroll.core.entity.ScanType;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
import com.payroll.web.repository.CloudWorkingDaysConfigRepository;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.EmployeeRepository;
import com.payroll.web.repository.MonthlyPayrollInputRepository;
import com.payroll.web.repository.SyncClientRepository;
import com.payroll.web.security.ApiKeyHasher;
import com.payroll.web.security.JwtService;
import com.payroll.web.sync.AttendanceSyncRequest;
import com.payroll.web.sync.EmployeeSyncRequest;
import com.payroll.web.sync.WorkingDaysSyncRequest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
class PayrollCalculationControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SyncClientRepository syncClientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private CloudWorkingDaysConfigRepository workingDaysConfigRepository;
    @Autowired private CloudDayLevelOTConfigRepository dayLevelOTConfigRepository;
    @Autowired private CloudOtEmployeeAuthorizationRepository otAuthorizationRepository;
    @Autowired private MonthlyPayrollInputRepository monthlyPayrollInputRepository;
    @Autowired private ApiKeyHasher apiKeyHasher;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    /** April 2026: 18 weekdays (Apr 1 = Wednesday), mirrors the desktop golden-case fixture. */
    private static final List<LocalDate> APRIL_2026_18_WEEKDAYS = List.of(
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3),
            LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 7), LocalDate.of(2026, 4, 8),
            LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 4, 16), LocalDate.of(2026, 4, 17),
            LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 21), LocalDate.of(2026, 4, 22),
            LocalDate.of(2026, 4, 23), LocalDate.of(2026, 4, 24));

    @BeforeEach
    void seed() {
        monthlyPayrollInputRepository.deleteAll();
        otAuthorizationRepository.deleteAll();
        dayLevelOTConfigRepository.deleteAll();
        workingDaysConfigRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        syncClientRepository.deleteAll();
        employeeRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Wood Lanka", "WL");
        companyB = saveCompany("DCH Plywood", "DCH");
        token = jwtService.generateToken("superadmin");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Company saveCompany(String name, String code) {
        Company c = new Company();
        c.setName(name);
        c.setCode(code);
        return companyRepository.save(c);
    }

    private String apiKeyFor(Long companyId) {
        String rawKey = "raw-key-for-company-" + companyId;
        SyncClient sc = new SyncClient();
        sc.setCompanyId(companyId);
        sc.setApiKeyHash(apiKeyHasher.hash(rawKey));
        sc.setActive(true);
        syncClientRepository.save(sc);
        return rawKey;
    }

    private Employee saveEmployee(Long companyId, String code, String name) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setEmployeeCode(code);
        e.setName(name);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1200.00"));
        e.setActive(true);
        return employeeRepository.save(e);
    }

    private void saveAttendance(Employee emp, LocalDate date, LocalTime entryTime, LocalTime exitTime) {
        AttendanceRecord entry = new AttendanceRecord();
        entry.setEmployee(emp);
        entry.setScanDatetime(LocalDateTime.of(date, entryTime));
        entry.setScanType(ScanType.ENTRY);
        attendanceRecordRepository.save(entry);

        AttendanceRecord exit = new AttendanceRecord();
        exit.setEmployee(emp);
        exit.setScanDatetime(LocalDateTime.of(date, exitTime));
        exit.setScanType(ScanType.EXIT);
        attendanceRecordRepository.save(exit);
    }

    private void saveWorkingDays(Long companyId, String periodMonth, int availableDays) {
        CloudWorkingDaysConfig config = new CloudWorkingDaysConfig();
        config.setCompanyId(companyId);
        config.setPeriodMonth(periodMonth);
        config.setAvailableWorkingDays(availableDays);
        config.setUpdatedBy("admin");
        config.setUpdatedAt(Instant.now());
        workingDaysConfigRepository.save(config);
    }

    private void saveDayLevelConfig(Long companyId, LocalDate date, DayType dayType, boolean allStaffOt) {
        CloudDayLevelOTConfig config = new CloudDayLevelOTConfig();
        config.setCompanyId(companyId);
        config.setConfigDate(date);
        config.setDayType(dayType);
        config.setAllStaffOt(allStaffOt);
        config.setSetBy(1L);
        config.setSetAt(Instant.now());
        dayLevelOTConfigRepository.save(config);
    }

    private void saveOtAuth(Long companyId, String employeeCode, LocalDate date, boolean authorized) {
        CloudOtEmployeeAuthorization auth = new CloudOtEmployeeAuthorization();
        auth.setCompanyId(companyId);
        auth.setEmployeeCode(employeeCode);
        auth.setAuthDate(date);
        auth.setAuthorized(authorized);
        auth.setSetBy("admin");
        auth.setSetAt(Instant.now());
        otAuthorizationRepository.save(auth);
    }

    private void saveMonthlyInput(Long companyId, String employeeCode, String periodMonth,
                                   String allowance, String salaryAdvance, String festivalAdvance) {
        MonthlyPayrollInput input = new MonthlyPayrollInput();
        input.setCompanyId(companyId);
        input.setEmployeeCode(employeeCode);
        input.setPeriodMonth(periodMonth);
        input.setAllowance(new BigDecimal(allowance));
        input.setSalaryAdvance(new BigDecimal(salaryAdvance));
        input.setFestivalAdvance(new BigDecimal(festivalAdvance));
        input.setUpdatedBy("admin");
        input.setUpdatedAt(Instant.now());
        monthlyPayrollInputRepository.save(input);
    }

    private String calcUrl(String periodMonth) {
        return "/api/payroll/calculate/" + periodMonth;
    }

    // ── golden case, through the full sync pipeline ─────────────────────────────

    @Test
    void goldenCase_throughFullCloudPipeline_gross24000_epf1920() throws Exception {
        String apiKey = apiKeyFor(companyA.getId());

        var employeeReq = new EmployeeSyncRequest(
                "EMP-001", "Vijitha Bandara", "RFID-001", EmployeeCategory.STANDARD,
                new BigDecimal("1200.00"), new BigDecimal("0.08"), new BigDecimal("0.12"),
                new BigDecimal("0.03"), true);
        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(employeeReq))))
                .andExpect(status().isOk());

        var workingDaysReq = new WorkingDaysSyncRequest("2026-04", 23, "admin", Instant.now());
        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(workingDaysReq))))
                .andExpect(status().isOk());

        List<AttendanceSyncRequest> attendanceReqs = new java.util.ArrayList<>();
        int i = 0;
        for (LocalDate day : APRIL_2026_18_WEEKDAYS) {
            attendanceReqs.add(new AttendanceSyncRequest(
                    "golden-entry-" + i, "EMP-001", LocalDateTime.of(day, LocalTime.of(8, 0)),
                    ScanType.ENTRY, null, null, null));
            attendanceReqs.add(new AttendanceSyncRequest(
                    "golden-exit-" + i, "EMP-001", LocalDateTime.of(day, LocalTime.of(17, 0)),
                    ScanType.EXIT, null, null, null));
            i++;
        }
        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attendanceReqs)))
                .andExpect(status().isOk());

        mockMvc.perform(get(calcUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$[0].availableWorkingDays").value(23))
                .andExpect(jsonPath("$[0].daysWorked").value(18))
                .andExpect(jsonPath("$[0].gross").value(24000.00))
                .andExpect(jsonPath("$[0].epfEmployee").value(1920.00))
                .andExpect(jsonPath("$[0].epfEmployer").value(2880.00))
                .andExpect(jsonPath("$[0].etf").value(720.00))
                .andExpect(jsonPath("$[0].workingDaysNotSet").value(false));
    }

    // ── OT + break-in-window regression (DEF-006 must NOT be inherited) ─────────

    @Test
    void otPay_breakInsideOtWindow_isDeducted() throws Exception {
        Employee emp = saveEmployee(companyA.getId(), "EMP-BRK", "Break Worker");
        saveWorkingDays(companyA.getId(), "2026-05", 22);
        // Force an OT_DAY classification regardless of the real calendar weekday, so the
        // entire 08:00-17:00 shift is OT and must have the tea/lunch breaks deducted from it.
        saveDayLevelConfig(companyA.getId(), LocalDate.of(2026, 5, 6), DayType.SUNDAY, false);
        saveAttendance(emp, LocalDate.of(2026, 5, 6), LocalTime.of(8, 0), LocalTime.of(17, 0));

        // 540 raw minutes - (20 morning tea + 30 lunch + 10 afternoon tea) = 480 -> 8.00h OT.
        // If breaks were NOT deducted (DEF-006), this would be 540 min = 9.00h / Rs 2025.00 instead.
        mockMvc.perform(get(calcUrl("2026-05"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-BRK"))
                .andExpect(jsonPath("$[0].otHours").value(8.00))
                .andExpect(jsonPath("$[0].otPay").value(1800.00));
    }

    // ── OT authorization gate (two-switch model) ─────────────────────────────────

    @Test
    void otAuthorizationGate_autoAuthorizedWhenNoSwitchSet_blockedWhenExplicitlyFalse() throws Exception {
        Employee autoEmp = saveEmployee(companyA.getId(), "EMP-AUTO", "Auto OT Worker");
        Employee blockedEmp = saveEmployee(companyA.getId(), "EMP-BLOCKED", "Blocked OT Worker");
        saveWorkingDays(companyA.getId(), "2026-06", 22);

        LocalDate noSwitchDay = LocalDate.of(2026, 6, 1);
        LocalDate switchSetDay = LocalDate.of(2026, 6, 2);
        saveDayLevelConfig(companyA.getId(), noSwitchDay, DayType.WEEKDAY, false);
        saveDayLevelConfig(companyA.getId(), switchSetDay, DayType.WEEKDAY, false);

        // Both work an identical late shift: 08:00-19:00 -> 2h OT beyond the 17:00 shift end.
        saveAttendance(autoEmp, noSwitchDay, LocalTime.of(8, 0), LocalTime.of(19, 0));
        saveAttendance(blockedEmp, switchSetDay, LocalTime.of(8, 0), LocalTime.of(19, 0));

        // No CloudOtEmployeeAuthorization row exists for noSwitchDay at all -> auto-authorized.
        // On switchSetDay, the switch IS set (this very row) and explicitly denies blockedEmp.
        saveOtAuth(companyA.getId(), "EMP-BLOCKED", switchSetDay, false);

        var result = mockMvc.perform(get(calcUrl("2026-06"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andReturn();

        List<PayrollCalculationResponse> rows = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PayrollCalculationResponse.class));

        PayrollCalculationResponse autoRow = rows.stream()
                .filter(r -> r.employeeCode().equals("EMP-AUTO")).findFirst().orElseThrow();
        PayrollCalculationResponse blockedRow = rows.stream()
                .filter(r -> r.employeeCode().equals("EMP-BLOCKED")).findFirst().orElseThrow();

        assertThat(autoRow.otHours()).isEqualByComparingTo("2.00");
        assertThat(autoRow.otPay()).isEqualByComparingTo("450.00");
        assertThat(blockedRow.otHours()).isEqualByComparingTo("0.00");
        assertThat(blockedRow.otPay()).isEqualByComparingTo("0.00");
    }

    // ── missing MonthlyPayrollInput defaults to zero ─────────────────────────────

    @Test
    void missingMonthlyPayrollInput_defaultsToZero_takeHomeStillCorrect() throws Exception {
        saveEmployee(companyA.getId(), "EMP-NOINPUT", "No Input Worker");
        saveWorkingDays(companyA.getId(), "2026-07", 20);
        // No attendance at all -> 0 days worked; no MonthlyPayrollInput row either.

        // gross = max(0, 30000 - 20*1200) = 6000; epfEmployee = 480; takeHome = 6000 - 480 = 5520
        mockMvc.perform(get(calcUrl("2026-07"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-NOINPUT"))
                .andExpect(jsonPath("$[0].allowance").value(0))
                .andExpect(jsonPath("$[0].salaryAdvance").value(0))
                .andExpect(jsonPath("$[0].festivalAdvance").value(0))
                .andExpect(jsonPath("$[0].gross").value(6000.00))
                .andExpect(jsonPath("$[0].epfEmployee").value(480.00))
                .andExpect(jsonPath("$[0].takeHome").value(5520.00));
    }

    // ── missing CloudWorkingDaysConfig ────────────────────────────────────────────

    @Test
    void missingWorkingDaysConfig_flagsRowAndNullsMoneyFields() throws Exception {
        saveEmployee(companyA.getId(), "EMP-NOWD", "No Working Days Worker");
        // No CloudWorkingDaysConfig row for 2026-08 at all.

        mockMvc.perform(get(calcUrl("2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-NOWD"))
                .andExpect(jsonPath("$[0].workingDaysNotSet").value(true))
                .andExpect(jsonPath("$[0].gross").doesNotExist())
                .andExpect(jsonPath("$[0].otPay").doesNotExist())
                .andExpect(jsonPath("$[0].epfEmployee").doesNotExist())
                .andExpect(jsonPath("$[0].takeHome").doesNotExist());
    }

    // ── take-home arithmetic, manually verified, with non-zero allowance + advances ─

    @Test
    void takeHomeFormula_withAllowanceAndBothAdvances_arithmeticIsCorrect() throws Exception {
        Employee emp = saveEmployee(companyA.getId(), "EMP-CALC", "Calc Worker");
        saveWorkingDays(companyA.getId(), "2026-04", 23);
        for (LocalDate day : APRIL_2026_18_WEEKDAYS) {
            saveAttendance(emp, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
        }
        saveMonthlyInput(companyA.getId(), "EMP-CALC", "2026-04", "1500.00", "800.00", "300.00");

        // gross = 24000, otPay = 0, epfEmployee = 1920
        // takeHome = 24000 + 0 + 1500 - 1920 - 800 - 300 = 22480.00
        mockMvc.perform(get(calcUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-CALC"))
                .andExpect(jsonPath("$[0].gross").value(24000.00))
                .andExpect(jsonPath("$[0].otPay").value(0.00))
                .andExpect(jsonPath("$[0].allowance").value(1500.00))
                .andExpect(jsonPath("$[0].salaryAdvance").value(800.00))
                .andExpect(jsonPath("$[0].festivalAdvance").value(300.00))
                .andExpect(jsonPath("$[0].takeHome").value(22480.00));
    }

    // ── auth ──────────────────────────────────────────────────────────────────

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(calcUrl("2026-08")).param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get(calcUrl("2026-08"))
                        .header("Authorization", "Bearer not-a-real-token")
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ── multi-company segregation ─────────────────────────────────────────────

    @Test
    void twoCompaniesSameEmployeeCodeStaySegregated() throws Exception {
        Employee empA = saveEmployee(companyA.getId(), "EMP-001", "Company A Worker");
        Employee empB = saveEmployee(companyB.getId(), "EMP-001", "Company B Worker");
        saveWorkingDays(companyA.getId(), "2026-04", 23);
        saveWorkingDays(companyB.getId(), "2026-04", 23);
        for (LocalDate day : APRIL_2026_18_WEEKDAYS) {
            saveAttendance(empA, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
        }
        // companyB's EMP-001 has no attendance at all -> gross = max(0, 30000 - 23*1200) = 2400

        mockMvc.perform(get(calcUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gross").value(24000.00));

        mockMvc.perform(get(calcUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gross").value(2400.00));
    }
}
