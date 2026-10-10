package com.payroll.web.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Company;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.EmployeeRepository;
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
import java.time.LocalDateTime;
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
class AttendanceControllerIT {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 6, 20, 8, 30);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private AuditLogEntryRepository auditLogEntryRepository;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    @BeforeEach
    void seed() {
        auditLogEntryRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        employeeRepository.deleteAll();
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

    private Employee saveEmployee(Company company, String employeeCode) {
        Employee emp = new Employee();
        emp.setCompanyId(company.getId());
        emp.setEmployeeCode(employeeCode);
        emp.setName("Test Worker");
        emp.setCategory(EmployeeCategory.STANDARD);
        emp.setGrossDailySalary(new BigDecimal("1200.00"));
        return employeeRepository.save(emp);
    }

    private AttendanceRecord saveScan(Employee employee, LocalDateTime when, ScanType type) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(employee);
        r.setScanDatetime(when);
        r.setScanType(type);
        return attendanceRecordRepository.save(r);
    }

    private String getUrl(Long companyId, String employeeCode, String date) {
        return "/api/attendance/" + companyId + "/" + employeeCode + "/" + date;
    }

    // --- auth ---

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-001", "2026-06-20")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-001", "2026-06-20"))
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/attendance/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/attendance/1")
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    // --- GET: scoping / MISSING_CLOCK_OUT ---

    @Test
    void getReturnsRecordsForEmployeeAndDate_scopedToCompany() throws Exception {
        Employee empA = saveEmployee(companyA, "EMP-001");
        Employee empB = saveEmployee(companyB, "EMP-001");
        saveScan(empA, BASE_TIME, ScanType.ENTRY);
        saveScan(empA, BASE_TIME.plusHours(8), ScanType.EXIT);
        saveScan(empB, BASE_TIME, ScanType.ENTRY);

        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-001", "2026-06-20"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].scanType").value("ENTRY"))
                .andExpect(jsonPath("$[0].missingClockOut").value(false))
                .andExpect(jsonPath("$[1].scanType").value("EXIT"));
    }

    @Test
    void getFlagsDanglingEntryAsMissingClockOut() throws Exception {
        Employee emp = saveEmployee(companyA, "EMP-002");
        saveScan(emp, BASE_TIME, ScanType.ENTRY);

        mockMvc.perform(get(getUrl(companyA.getId(), "EMP-002", "2026-06-20"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].missingClockOut").value(true));
    }

    @Test
    void getForUnknownEmployeeReturnsEmptyList() throws Exception {
        mockMvc.perform(get(getUrl(companyA.getId(), "NO-SUCH-EMP", "2026-06-20"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- PUT: correction + audit ---

    @Test
    void putWithValidReason_updatesRecordAndWritesAudit() throws Exception {
        Employee emp = saveEmployee(companyA, "EMP-003");
        AttendanceRecord scan = saveScan(emp, BASE_TIME, ScanType.ENTRY);
        LocalDateTime corrected = BASE_TIME.plusMinutes(10);

        var body = new TimestampCorrectionRequest(companyA.getId(), corrected, "Clock was slow");

        mockMvc.perform(put("/api/attendance/" + scan.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanDatetime").value("2026-06-20T08:40:00"));

        AttendanceRecord updated = attendanceRecordRepository.findById(scan.getId()).orElseThrow();
        assertThat(updated.getScanDatetime()).isEqualTo(corrected);
        assertThat(updated.getOriginalScanDatetime()).isEqualTo(BASE_TIME);
        assertThat(updated.getCorrectionNote()).isEqualTo("Clock was slow");

        var audits = auditLogEntryRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getAction()).isEqualTo("TIMESTAMP_CORRECTED");
        assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
        assertThat(audits.get(0).getReason()).isEqualTo("Clock was slow");
        assertThat(audits.get(0).getOldValue()).contains(BASE_TIME.toString());
        assertThat(audits.get(0).getNewValue()).contains(corrected.toString());
    }

    @Test
    void putWithBlankReason_returns400_doesNotUpdateOrWriteAudit() throws Exception {
        Employee emp = saveEmployee(companyA, "EMP-004");
        AttendanceRecord scan = saveScan(emp, BASE_TIME, ScanType.ENTRY);

        var body = new TimestampCorrectionRequest(companyA.getId(), BASE_TIME.plusMinutes(10), "");

        mockMvc.perform(put("/api/attendance/" + scan.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        AttendanceRecord unchanged = attendanceRecordRepository.findById(scan.getId()).orElseThrow();
        assertThat(unchanged.getScanDatetime()).isEqualTo(BASE_TIME);
        assertThat(unchanged.getOriginalScanDatetime()).isNull();
        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void putWithMissingReason_returns400() throws Exception {
        Employee emp = saveEmployee(companyA, "EMP-005");
        AttendanceRecord scan = saveScan(emp, BASE_TIME, ScanType.ENTRY);

        String bodyJson = "{\"companyId\":" + companyA.getId()
                + ",\"newScanDatetime\":\"" + BASE_TIME.plusMinutes(10) + "\"}";

        mockMvc.perform(put("/api/attendance/" + scan.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isBadRequest());

        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void putOnRecordBelongingToDifferentCompanyIsRejected() throws Exception {
        Employee empB = saveEmployee(companyB, "EMP-006");
        AttendanceRecord scan = saveScan(empB, BASE_TIME, ScanType.ENTRY);

        // Caller supplies companyA even though the record actually belongs to companyB.
        var body = new TimestampCorrectionRequest(companyA.getId(), BASE_TIME.plusMinutes(10), "Attempted cross-company edit");

        mockMvc.perform(put("/api/attendance/" + scan.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        AttendanceRecord unchanged = attendanceRecordRepository.findById(scan.getId()).orElseThrow();
        assertThat(unchanged.getScanDatetime()).isEqualTo(BASE_TIME);
        assertThat(auditLogEntryRepository.findAll()).isEmpty();
    }

    @Test
    void editingTwice_originalScanDatetimeIsNeverOverwritten() throws Exception {
        Employee emp = saveEmployee(companyA, "EMP-007");
        AttendanceRecord scan = saveScan(emp, BASE_TIME, ScanType.ENTRY);

        var firstEdit = new TimestampCorrectionRequest(companyA.getId(), BASE_TIME.plusMinutes(5), "First fix");
        mockMvc.perform(put("/api/attendance/" + scan.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstEdit)))
                .andExpect(status().isOk());

        var secondEdit = new TimestampCorrectionRequest(companyA.getId(), BASE_TIME.plusMinutes(15), "Second fix");
        mockMvc.perform(put("/api/attendance/" + scan.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondEdit)))
                .andExpect(status().isOk());

        AttendanceRecord updated = attendanceRecordRepository.findById(scan.getId()).orElseThrow();
        assertThat(updated.getScanDatetime()).isEqualTo(BASE_TIME.plusMinutes(15));
        assertThat(updated.getOriginalScanDatetime()).isEqualTo(BASE_TIME);
        assertThat(auditLogEntryRepository.findAll()).hasSize(2);
    }
}
