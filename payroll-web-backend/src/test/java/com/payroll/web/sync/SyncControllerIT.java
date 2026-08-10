package com.payroll.web.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.Company;
import com.payroll.core.entity.DayType;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
import com.payroll.web.repository.CloudWorkingDaysConfigRepository;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.EmployeeRepository;
import com.payroll.web.repository.SyncClientRepository;
import com.payroll.web.security.ApiKeyHasher;
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
import java.util.List;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class SyncControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SyncClientRepository syncClientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private CloudDayLevelOTConfigRepository dayLevelOTConfigRepository;
    @Autowired private CloudOtEmployeeAuthorizationRepository otAuthorizationRepository;
    @Autowired private CloudWorkingDaysConfigRepository workingDaysConfigRepository;
    @Autowired private ApiKeyHasher apiKeyHasher;

    private Company companyA;
    private Company companyB;
    private String apiKeyA;
    private String apiKeyB;
    private Employee employeeA;

    @BeforeEach
    void seed() {
        attendanceRecordRepository.deleteAll();
        otAuthorizationRepository.deleteAll();
        workingDaysConfigRepository.deleteAll();
        dayLevelOTConfigRepository.deleteAll();
        employeeRepository.deleteAll();
        syncClientRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Wood Lanka", "WL");
        companyB = saveCompany("DCH Plywood", "DCH");

        apiKeyA = "raw-key-for-company-a";
        apiKeyB = "raw-key-for-company-b";
        saveSyncClient(companyA.getId(), apiKeyA, true);
        saveSyncClient(companyB.getId(), apiKeyB, true);

        employeeA = saveEmployee(companyA.getId(), "EMP-001");
    }

    private Company saveCompany(String name, String code) {
        Company c = new Company();
        c.setName(name);
        c.setCode(code);
        return companyRepository.save(c);
    }

    private SyncClient saveSyncClient(Long companyId, String rawKey, boolean active) {
        SyncClient sc = new SyncClient();
        sc.setCompanyId(companyId);
        sc.setApiKeyHash(apiKeyHasher.hash(rawKey));
        sc.setActive(active);
        return syncClientRepository.save(sc);
    }

    private Employee saveEmployee(Long companyId, String code) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setEmployeeCode(code);
        e.setName("Worker " + code);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1200.00"));
        return employeeRepository.save(e);
    }

    private AttendanceSyncRequest recordFor(String uuid, String employeeCode, LocalDateTime dt, ScanType type) {
        return new AttendanceSyncRequest(uuid, employeeCode, dt, type, null, null, null);
    }

    private EmployeeSyncRequest employeeRequestFor(String employeeCode, String name, String rfidCardId) {
        return new EmployeeSyncRequest(employeeCode, name, rfidCardId, EmployeeCategory.STANDARD,
                new BigDecimal("1500.00"), new BigDecimal("0.08"), new BigDecimal("0.12"),
                new BigDecimal("0.03"), true);
    }

    private DayLevelOtSyncRequest dayLevelOtRequestFor(LocalDate date, boolean isAllStaffOt, DayType dayType) {
        return new DayLevelOtSyncRequest(date, isAllStaffOt, dayType, 1L, Instant.parse("2026-08-01T00:00:00Z"));
    }

    private OtAuthorizationSyncRequest otAuthRequestFor(String employeeCode, LocalDate authDate, boolean authorized) {
        return new OtAuthorizationSyncRequest(employeeCode, authDate, authorized, "admin",
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    private WorkingDaysSyncRequest workingDaysRequestFor(String periodMonth, int availableWorkingDays) {
        return new WorkingDaysSyncRequest(periodMonth, availableWorkingDays, "admin",
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    // --- auth ---

    @Test
    void missingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void invalidApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveApiKeyReturns401() throws Exception {
        String inactiveKey = "inactive-key";
        saveSyncClient(companyA.getId(), inactiveKey, false);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", inactiveKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- insert / idempotency / correction ---

    @Test
    void validKeyInsertsNewRecordTaggedWithCorrectCompany() throws Exception {
        var req = recordFor("uuid-1", "EMP-001", LocalDateTime.of(2026, 8, 1, 8, 0), ScanType.ENTRY);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].syncUuid").value("uuid-1"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].cloudRecordId").isNotEmpty());

        var saved = attendanceRecordRepository.findBySyncUuid("uuid-1").orElseThrow();
        var savedEmployee = employeeRepository.findById(saved.getEmployee().getId()).orElseThrow();
        assertThat(savedEmployee.getCompanyId()).isEqualTo(companyA.getId());
    }

    @Test
    void identicalRePushIsNoOpWithoutDuplicate() throws Exception {
        var req = recordFor("uuid-2", "EMP-001", LocalDateTime.of(2026, 8, 1, 8, 0), ScanType.ENTRY);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NO_OP"));

        assertThat(attendanceRecordRepository.findAll()).hasSize(1);
    }

    @Test
    void differentDataForSameUuidUpdatesInPlaceAndPreservesOriginal() throws Exception {
        LocalDateTime original = LocalDateTime.of(2026, 8, 1, 8, 0);
        LocalDateTime corrected = LocalDateTime.of(2026, 8, 1, 8, 15);
        var initial = recordFor("uuid-3", "EMP-001", original, ScanType.ENTRY);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(initial))))
                .andExpect(status().isOk());

        var correction = new AttendanceSyncRequest("uuid-3", "EMP-001", corrected, ScanType.ENTRY,
                original, "clocked in late due to gate delay", 99L);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(correction))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UPDATED"));

        assertThat(attendanceRecordRepository.findAll()).hasSize(1);
        var saved = attendanceRecordRepository.findBySyncUuid("uuid-3").orElseThrow();
        assertThat(saved.getScanDatetime()).isEqualTo(corrected);
        assertThat(saved.getOriginalScanDatetime()).isEqualTo(original);
        assertThat(saved.getCorrectionNote()).isEqualTo("clocked in late due to gate delay");
        assertThat(saved.getCorrectedBy()).isEqualTo(99L);
    }

    // --- partial batch failure ---

    @Test
    void batchWithOneUnknownEmployeeCodeRejectsOnlyThatRecord() throws Exception {
        var good1 = recordFor("uuid-good-1", "EMP-001", LocalDateTime.of(2026, 8, 1, 8, 0), ScanType.ENTRY);
        var bad = recordFor("uuid-bad", "NO-SUCH-EMPLOYEE", LocalDateTime.of(2026, 8, 1, 8, 5), ScanType.ENTRY);
        var good2 = recordFor("uuid-good-2", "EMP-001", LocalDateTime.of(2026, 8, 1, 17, 0), ScanType.EXIT);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(good1, bad, good2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].syncUuid").value("uuid-good-1"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].syncUuid").value("uuid-bad"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").isNotEmpty())
                .andExpect(jsonPath("$[2].syncUuid").value("uuid-good-2"))
                .andExpect(jsonPath("$[2].status").value("ACCEPTED"));

        assertThat(attendanceRecordRepository.findBySyncUuid("uuid-good-1")).isPresent();
        assertThat(attendanceRecordRepository.findBySyncUuid("uuid-good-2")).isPresent();
        assertThat(attendanceRecordRepository.findBySyncUuid("uuid-bad")).isEmpty();
    }

    // --- cross-company segregation ---

    @Test
    void twoFactoriesKeysSegregateRecordsByCompany() throws Exception {
        Employee employeeB = saveEmployee(companyB.getId(), "EMP-001");

        var reqA = recordFor("uuid-a", "EMP-001", LocalDateTime.of(2026, 8, 1, 8, 0), ScanType.ENTRY);
        var reqB = recordFor("uuid-b", "EMP-001", LocalDateTime.of(2026, 8, 1, 9, 0), ScanType.ENTRY);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var savedA = attendanceRecordRepository.findBySyncUuid("uuid-a").orElseThrow();
        var savedB = attendanceRecordRepository.findBySyncUuid("uuid-b").orElseThrow();
        assertThat(savedA.getEmployee().getId()).isEqualTo(employeeA.getId());
        assertThat(savedB.getEmployee().getId()).isEqualTo(employeeB.getId());
        assertThat(employeeRepository.findById(savedA.getEmployee().getId()).orElseThrow().getCompanyId())
                .isEqualTo(companyA.getId());
        assertThat(employeeRepository.findById(savedB.getEmployee().getId()).orElseThrow().getCompanyId())
                .isEqualTo(companyB.getId());
    }

    @Test
    void companyACannotSyncCompanyBsEmployeeCode() throws Exception {
        saveEmployee(companyB.getId(), "ONLY-IN-B");
        var req = recordFor("uuid-cross", "ONLY-IN-B", LocalDateTime.of(2026, 8, 1, 8, 0), ScanType.ENTRY);

        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
    }

    // --- employee sync: auth ---

    @Test
    void employeesMissingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeesInvalidApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- employee sync: insert / update / segregation / partial failure ---

    @Test
    void newEmployeeInsertedWithCorrectCompanyId() throws Exception {
        var req = employeeRequestFor("EMP-100", "New Worker", "RFID-100");

        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-100"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var saved = employeeRepository.findByEmployeeCodeAndCompanyId("EMP-100", companyA.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("New Worker");
        assertThat(saved.getCompanyId()).isEqualTo(companyA.getId());
    }

    @Test
    void existingEmployeeIsUpdatedNotDuplicated() throws Exception {
        var initial = employeeRequestFor("EMP-001", "Worker EMP-001", "RFID-EMP-001-v1");
        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(initial))))
                .andExpect(status().isOk());

        var updated = new EmployeeSyncRequest("EMP-001", "Updated Name", "RFID-EMP-001-v2",
                EmployeeCategory.PEELING, new BigDecimal("2000.00"), new BigDecimal("0.08"),
                new BigDecimal("0.12"), new BigDecimal("0.03"), false);

        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(updated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UPDATED"));

        var all = employeeRepository.findAll();
        assertThat(all).hasSize(1);
        var saved = all.get(0);
        assertThat(saved.getId()).isEqualTo(employeeA.getId());
        assertThat(saved.getName()).isEqualTo("Updated Name");
        assertThat(saved.getCategory()).isEqualTo(EmployeeCategory.PEELING);
        assertThat(saved.getGrossDailySalary()).isEqualByComparingTo("2000.00");
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void twoCompaniesSameEmployeeCodeStaySegregated() throws Exception {
        var reqA = employeeRequestFor("SHARED-CODE", "Worker A", "RFID-SHARED-A");
        var reqB = employeeRequestFor("SHARED-CODE", "Worker B", "RFID-SHARED-B");

        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var savedA = employeeRepository.findByEmployeeCodeAndCompanyId("SHARED-CODE", companyA.getId()).orElseThrow();
        var savedB = employeeRepository.findByEmployeeCodeAndCompanyId("SHARED-CODE", companyB.getId()).orElseThrow();
        assertThat(savedA.getId()).isNotEqualTo(savedB.getId());
        assertThat(savedA.getName()).isEqualTo("Worker A");
        assertThat(savedB.getName()).isEqualTo("Worker B");
    }

    @Test
    void oneBadRecordInEmployeeBatchDoesNotBlockOthers() throws Exception {
        // employeeA already has no rfidCardId set; give a duplicate rfidCardId to two
        // records in the same batch so the second collides on the global unique constraint.
        var good1 = employeeRequestFor("EMP-200", "Good Worker 1", "RFID-DUP");
        var bad = employeeRequestFor("EMP-201", "Bad Worker", "RFID-DUP");
        var good2 = employeeRequestFor("EMP-202", "Good Worker 2", "RFID-202");

        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(good1, bad, good2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-200"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].employeeCode").value("EMP-201"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").isNotEmpty())
                .andExpect(jsonPath("$[2].employeeCode").value("EMP-202"))
                .andExpect(jsonPath("$[2].status").value("ACCEPTED"));

        assertThat(employeeRepository.findByEmployeeCodeAndCompanyId("EMP-200", companyA.getId())).isPresent();
        assertThat(employeeRepository.findByEmployeeCodeAndCompanyId("EMP-201", companyA.getId())).isEmpty();
        assertThat(employeeRepository.findByEmployeeCodeAndCompanyId("EMP-202", companyA.getId())).isPresent();
    }

    // --- integration: employee sync unblocks attendance sync for the same employeeCode ---

    @Test
    void employeeSyncThenAttendanceSyncForNewEmployeeCodeIsAccepted() throws Exception {
        var employeeReq = employeeRequestFor("EMP-300", "Brand New Worker", "RFID-300");
        mockMvc.perform(post("/api/sync/employees")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(employeeReq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var attendanceReq = recordFor("uuid-300", "EMP-300", LocalDateTime.of(2026, 8, 2, 8, 0), ScanType.ENTRY);
        mockMvc.perform(post("/api/sync/attendance")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(attendanceReq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        assertThat(attendanceRecordRepository.findBySyncUuid("uuid-300")).isPresent();
    }

    // --- day-level OT sync: auth ---

    @Test
    void dayLevelOtMissingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/day-level-ot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dayLevelOtInvalidApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- day-level OT sync: upsert / segregation / partial failure ---

    @Test
    void dayLevelOtUpsertInsertsNewConfig() throws Exception {
        var req = dayLevelOtRequestFor(LocalDate.of(2026, 8, 2), false, DayType.SUNDAY);

        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configDate").value("2026-08-02"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var saved = dayLevelOTConfigRepository
                .findByCompanyIdAndConfigDate(companyA.getId(), LocalDate.of(2026, 8, 2)).orElseThrow();
        assertThat(saved.getDayType()).isEqualTo(DayType.SUNDAY);
        assertThat(saved.isAllStaffOt()).isFalse();
    }

    @Test
    void dayLevelOtUpsertUpdatesExistingConfig() throws Exception {
        var initial = dayLevelOtRequestFor(LocalDate.of(2026, 8, 3), false, DayType.WEEKDAY);
        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(initial))))
                .andExpect(status().isOk());

        // Retroactively marked as a mercantile holiday.
        var updated = dayLevelOtRequestFor(LocalDate.of(2026, 8, 3), true, DayType.MERCANTILE_HOLIDAY);
        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(updated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UPDATED"));

        assertThat(dayLevelOTConfigRepository.findAll()).hasSize(1);
        var saved = dayLevelOTConfigRepository
                .findByCompanyIdAndConfigDate(companyA.getId(), LocalDate.of(2026, 8, 3)).orElseThrow();
        assertThat(saved.getDayType()).isEqualTo(DayType.MERCANTILE_HOLIDAY);
        assertThat(saved.isAllStaffOt()).isTrue();
    }

    @Test
    void twoCompaniesSameConfigDateStaySegregated() throws Exception {
        var reqA = dayLevelOtRequestFor(LocalDate.of(2026, 8, 4), false, DayType.SPECIAL);
        var reqB = dayLevelOtRequestFor(LocalDate.of(2026, 8, 4), true, DayType.MERCANTILE_HOLIDAY);

        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", apiKeyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var savedA = dayLevelOTConfigRepository
                .findByCompanyIdAndConfigDate(companyA.getId(), LocalDate.of(2026, 8, 4)).orElseThrow();
        var savedB = dayLevelOTConfigRepository
                .findByCompanyIdAndConfigDate(companyB.getId(), LocalDate.of(2026, 8, 4)).orElseThrow();
        assertThat(savedA.getDayType()).isEqualTo(DayType.SPECIAL);
        assertThat(savedB.getDayType()).isEqualTo(DayType.MERCANTILE_HOLIDAY);
    }

    @Test
    void dayLevelOtBatchWithOneBadRecordRejectsOnlyThatRecord() throws Exception {
        var good1 = dayLevelOtRequestFor(LocalDate.of(2026, 8, 5), false, DayType.WEEKDAY);
        var bad = new DayLevelOtSyncRequest(null, false, DayType.WEEKDAY, 1L, Instant.parse("2026-08-01T00:00:00Z"));
        var good2 = dayLevelOtRequestFor(LocalDate.of(2026, 8, 6), false, DayType.WEEKDAY);

        mockMvc.perform(post("/api/sync/day-level-ot")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(good1, bad, good2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").isNotEmpty())
                .andExpect(jsonPath("$[2].status").value("ACCEPTED"));

        assertThat(dayLevelOTConfigRepository.findByCompanyIdAndConfigDate(companyA.getId(), LocalDate.of(2026, 8, 5)))
                .isPresent();
        assertThat(dayLevelOTConfigRepository.findByCompanyIdAndConfigDate(companyA.getId(), LocalDate.of(2026, 8, 6)))
                .isPresent();
    }

    // --- OT authorization sync: auth ---

    @Test
    void otAuthMissingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otAuthInvalidApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- OT authorization sync: upsert / segregation / partial failure ---

    @Test
    void otAuthUpsertInsertsNewAuthorization() throws Exception {
        var req = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 2), true);

        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var saved = otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-001", LocalDate.of(2026, 8, 2))
                .orElseThrow();
        assertThat(saved.isAuthorized()).isTrue();
    }

    @Test
    void otAuthUpsertUpdatesExistingAuthorization() throws Exception {
        var initial = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 3), true);
        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(initial))))
                .andExpect(status().isOk());

        var revoked = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 3), false);
        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(revoked))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UPDATED"));

        assertThat(otAuthorizationRepository.findAll()).hasSize(1);
        var saved = otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-001", LocalDate.of(2026, 8, 3))
                .orElseThrow();
        assertThat(saved.isAuthorized()).isFalse();
    }

    @Test
    void twoCompaniesSameEmployeeCodeAndDateStaySegregatedForOtAuth() throws Exception {
        saveEmployee(companyB.getId(), "EMP-001");
        var reqA = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 4), true);
        var reqB = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 4), false);

        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", apiKeyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var savedA = otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-001", LocalDate.of(2026, 8, 4))
                .orElseThrow();
        var savedB = otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyB.getId(), "EMP-001", LocalDate.of(2026, 8, 4))
                .orElseThrow();
        assertThat(savedA.isAuthorized()).isTrue();
        assertThat(savedB.isAuthorized()).isFalse();
    }

    @Test
    void otAuthBatchWithUnknownEmployeeCodeRejectsOnlyThatRecord() throws Exception {
        var good1 = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 5), true);
        var bad = otAuthRequestFor("NO-SUCH-EMPLOYEE", LocalDate.of(2026, 8, 5), true);
        var good2 = otAuthRequestFor("EMP-001", LocalDate.of(2026, 8, 6), true);

        mockMvc.perform(post("/api/sync/ot-authorizations")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(good1, bad, good2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").isNotEmpty())
                .andExpect(jsonPath("$[2].status").value("ACCEPTED"));

        assertThat(otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-001", LocalDate.of(2026, 8, 5)))
                .isPresent();
        assertThat(otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "EMP-001", LocalDate.of(2026, 8, 6)))
                .isPresent();
        assertThat(otAuthorizationRepository
                .findByCompanyIdAndEmployeeCodeAndAuthDate(companyA.getId(), "NO-SUCH-EMPLOYEE", LocalDate.of(2026, 8, 5)))
                .isEmpty();
    }

    // --- working-days sync: auth ---

    @Test
    void workingDaysMissingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/working-days")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workingDaysInvalidApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- working-days sync: upsert / segregation / partial failure ---

    @Test
    void workingDaysUpsertInsertsNewConfig() throws Exception {
        var req = workingDaysRequestFor("2026-08", 25);

        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodMonth").value("2026-08"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var saved = workingDaysConfigRepository
                .findByCompanyIdAndPeriodMonth(companyA.getId(), "2026-08").orElseThrow();
        assertThat(saved.getAvailableWorkingDays()).isEqualTo(25);
        assertThat(saved.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void workingDaysUpsertUpdatesExistingConfig() throws Exception {
        var initial = workingDaysRequestFor("2026-09", 24);
        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(initial))))
                .andExpect(status().isOk());

        // Corrected after a public holiday was reclassified.
        var updated = workingDaysRequestFor("2026-09", 23);
        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(updated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UPDATED"));

        assertThat(workingDaysConfigRepository.findAll()).hasSize(1);
        var saved = workingDaysConfigRepository
                .findByCompanyIdAndPeriodMonth(companyA.getId(), "2026-09").orElseThrow();
        assertThat(saved.getAvailableWorkingDays()).isEqualTo(23);
    }

    @Test
    void twoCompaniesSamePeriodMonthStaySegregatedForWorkingDays() throws Exception {
        var reqA = workingDaysRequestFor("2026-10", 26);
        var reqB = workingDaysRequestFor("2026-10", 22);

        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKeyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(reqB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));

        var savedA = workingDaysConfigRepository
                .findByCompanyIdAndPeriodMonth(companyA.getId(), "2026-10").orElseThrow();
        var savedB = workingDaysConfigRepository
                .findByCompanyIdAndPeriodMonth(companyB.getId(), "2026-10").orElseThrow();
        assertThat(savedA.getAvailableWorkingDays()).isEqualTo(26);
        assertThat(savedB.getAvailableWorkingDays()).isEqualTo(22);
    }

    @Test
    void workingDaysBatchWithOneBadRecordRejectsOnlyThatRecord() throws Exception {
        var good1 = workingDaysRequestFor("2026-11", 25);
        var bad = new WorkingDaysSyncRequest(null, 25, "admin", Instant.parse("2026-08-01T00:00:00Z"));
        var good2 = workingDaysRequestFor("2026-12", 24);

        mockMvc.perform(post("/api/sync/working-days")
                        .header("X-API-Key", apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(good1, bad, good2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").isNotEmpty())
                .andExpect(jsonPath("$[2].status").value("ACCEPTED"));

        assertThat(workingDaysConfigRepository.findByCompanyIdAndPeriodMonth(companyA.getId(), "2026-11"))
                .isPresent();
        assertThat(workingDaysConfigRepository.findByCompanyIdAndPeriodMonth(companyA.getId(), "2026-12"))
                .isPresent();
    }
}
