package com.payroll.web.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.Company;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.AttendanceRecordRepository;
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
    @Autowired private ApiKeyHasher apiKeyHasher;

    private Company companyA;
    private Company companyB;
    private String apiKeyA;
    private String apiKeyB;
    private Employee employeeA;

    @BeforeEach
    void seed() {
        attendanceRecordRepository.deleteAll();
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
}
