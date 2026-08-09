package com.payroll.web.calculation;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.CloudWorkingDaysConfig;
import com.payroll.core.entity.Company;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.MonthlyPayrollInput;
import com.payroll.core.entity.ScanType;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
import com.payroll.web.repository.CloudWorkingDaysConfigRepository;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.EmployeeRepository;
import com.payroll.web.repository.MonthlyPayrollInputRepository;
import com.payroll.web.repository.SyncClientRepository;
import com.payroll.web.security.JwtService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class PayrollExportControllerIT {

    private static final String[] EXPECTED_HEADERS = {
            "Employee Code", "Name", "Available Working Days", "Days Worked", "OT Hours",
            "OT Pay (Rs)", "Gross (Rs)", "EPF Employee 8% (Rs)", "EPF Employer 12% (Rs)",
            "ETF 3% (Rs)", "Allowance (Rs)", "Salary Advance (Rs)", "Festival Advance (Rs)",
            "Take-Home (Rs)"
    };

    private static final int COL_EMPLOYEE_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_GROSS = 6;
    private static final int COL_EPF_EMPLOYEE = 7;
    private static final int COL_EPF_EMPLOYER = 8;
    private static final int COL_ETF = 9;
    private static final int COL_OT_PAY = 5;
    private static final int COL_ALLOWANCE = 10;
    private static final int COL_SALARY_ADVANCE = 11;
    private static final int COL_FESTIVAL_ADVANCE = 12;
    private static final int COL_TAKE_HOME = 13;

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SyncClientRepository syncClientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private CloudWorkingDaysConfigRepository workingDaysConfigRepository;
    @Autowired private CloudDayLevelOTConfigRepository dayLevelOTConfigRepository;
    @Autowired private CloudOtEmployeeAuthorizationRepository otAuthorizationRepository;
    @Autowired private MonthlyPayrollInputRepository monthlyPayrollInputRepository;
    @Autowired private JwtService jwtService;

    private Company companyA;
    private Company companyB;
    private String token;

    /** April 2026: 18 weekdays (Apr 1 = Wednesday), mirrors the calculation IT's golden fixture. */
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

    private String exportUrl(String periodMonth) {
        return "/api/payroll/export/" + periodMonth;
    }

    private Sheet parseWorkbook(MvcResult result) throws Exception {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes));
        return wb.getSheetAt(0);
    }

    private double numeric(Row row, int col) {
        return row.getCell(col).getNumericCellValue();
    }

    // ── golden case ───────────────────────────────────────────────────────────

    @Test
    void goldenCase_gross24000_epf1920_inCorrectColumns() throws Exception {
        Employee emp = saveEmployee(companyA.getId(), "EMP-001", "Vijitha Bandara");
        saveWorkingDays(companyA.getId(), "2026-04", 23);
        for (LocalDate day : APRIL_2026_18_WEEKDAYS) {
            saveAttendance(emp, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
        }

        MvcResult result = mockMvc.perform(get(exportUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andReturn();

        Sheet sheet = parseWorkbook(result);
        Row dataRow = sheet.getRow(1);
        assertThat(dataRow.getCell(COL_EMPLOYEE_CODE).getStringCellValue()).isEqualTo("EMP-001");
        assertThat(numeric(dataRow, COL_GROSS)).isCloseTo(24000.00, offset(0.001));
        assertThat(numeric(dataRow, COL_EPF_EMPLOYEE)).isCloseTo(1920.00, offset(0.001));
        assertThat(numeric(dataRow, COL_EPF_EMPLOYER)).isCloseTo(2880.00, offset(0.001));
        assertThat(numeric(dataRow, COL_ETF)).isCloseTo(720.00, offset(0.001));
    }

    // ── column headers ───────────────────────────────────────────────────────

    @Test
    void headers_matchExpectedOrderAndNames() throws Exception {
        MvcResult result = mockMvc.perform(get(exportUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andReturn();

        Sheet sheet = parseWorkbook(result);
        Row headerRow = sheet.getRow(0);
        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            assertThat(headerRow.getCell(i).getStringCellValue()).isEqualTo(EXPECTED_HEADERS[i]);
        }
    }

    // ── totals row arithmetic ────────────────────────────────────────────────

    @Test
    void totalsRow_matchesManualSum_forMultipleEmployees() throws Exception {
        Employee emp1 = saveEmployee(companyA.getId(), "EMP-A", "Employee A");
        Employee emp2 = saveEmployee(companyA.getId(), "EMP-B", "Employee B");
        saveWorkingDays(companyA.getId(), "2026-04", 23);
        for (LocalDate day : APRIL_2026_18_WEEKDAYS) {
            saveAttendance(emp1, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
            saveAttendance(emp2, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
        }
        saveMonthlyInput(companyA.getId(), "EMP-A", "2026-04", "1500.00", "800.00", "300.00");

        MvcResult result = mockMvc.perform(get(exportUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andReturn();

        Sheet sheet = parseWorkbook(result);
        Row row1 = sheet.getRow(1);
        Row row2 = sheet.getRow(2);
        Row totalsRow = sheet.getRow(3);

        assertThat(totalsRow.getCell(COL_EMPLOYEE_CODE).getStringCellValue()).isEqualTo("Total");
        assertThat(numeric(totalsRow, COL_GROSS))
                .isCloseTo(numeric(row1, COL_GROSS) + numeric(row2, COL_GROSS), offset(0.001));
        assertThat(numeric(totalsRow, COL_EPF_EMPLOYEE))
                .isCloseTo(numeric(row1, COL_EPF_EMPLOYEE) + numeric(row2, COL_EPF_EMPLOYEE), offset(0.001));
        assertThat(numeric(totalsRow, COL_TAKE_HOME))
                .isCloseTo(numeric(row1, COL_TAKE_HOME) + numeric(row2, COL_TAKE_HOME), offset(0.001));
        assertThat(numeric(totalsRow, COL_ALLOWANCE))
                .isCloseTo(numeric(row1, COL_ALLOWANCE) + numeric(row2, COL_ALLOWANCE), offset(0.001));
    }

    // ── WORKING_DAYS_NOT_SET renders N/A ─────────────────────────────────────

    @Test
    void missingWorkingDaysConfig_rendersNA_inMoneyColumns() throws Exception {
        saveEmployee(companyA.getId(), "EMP-NOWD", "No Working Days Worker");
        // No CloudWorkingDaysConfig row for 2026-08 at all.

        MvcResult result = mockMvc.perform(get(exportUrl("2026-08"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andReturn();

        Sheet sheet = parseWorkbook(result);
        Row dataRow = sheet.getRow(1);
        assertThat(dataRow.getCell(COL_EMPLOYEE_CODE).getStringCellValue()).isEqualTo("EMP-NOWD");

        int[] moneyColumns = {COL_OT_PAY, COL_GROSS, COL_EPF_EMPLOYEE, COL_EPF_EMPLOYER, COL_ETF,
                COL_ALLOWANCE, COL_SALARY_ADVANCE, COL_FESTIVAL_ADVANCE, COL_TAKE_HOME};
        for (int col : moneyColumns) {
            Cell cell = dataRow.getCell(col);
            assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(cell.getStringCellValue()).isEqualTo("N/A");
        }
    }

    // ── auth ──────────────────────────────────────────────────────────────────

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get(exportUrl("2026-08")).param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get(exportUrl("2026-08"))
                        .header("Authorization", "Bearer not-a-real-token")
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ── multi-company segregation ─────────────────────────────────────────────

    @Test
    void twoCompaniesSameEmployeeCodeStaySegregated() throws Exception {
        Employee empA = saveEmployee(companyA.getId(), "EMP-001", "Company A Worker");
        saveEmployee(companyB.getId(), "EMP-001", "Company B Worker");
        saveWorkingDays(companyA.getId(), "2026-04", 23);
        saveWorkingDays(companyB.getId(), "2026-04", 23);
        for (LocalDate day : APRIL_2026_18_WEEKDAYS) {
            saveAttendance(empA, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
        }
        // companyB's EMP-001 has no attendance at all -> gross = max(0, 30000 - 23*1200) = 2400

        MvcResult resultA = mockMvc.perform(get(exportUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andReturn();
        Sheet sheetA = parseWorkbook(resultA);
        assertThat(sheetA.getPhysicalNumberOfRows()).isEqualTo(3); // header + 1 employee + totals
        assertThat(sheetA.getRow(1).getCell(COL_NAME).getStringCellValue()).isEqualTo("Company A Worker");
        assertThat(numeric(sheetA.getRow(1), COL_GROSS)).isCloseTo(24000.00, offset(0.001));

        MvcResult resultB = mockMvc.perform(get(exportUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyB.getId())))
                .andExpect(status().isOk())
                .andReturn();
        Sheet sheetB = parseWorkbook(resultB);
        assertThat(sheetB.getRow(1).getCell(COL_NAME).getStringCellValue()).isEqualTo("Company B Worker");
        assertThat(numeric(sheetB.getRow(1), COL_GROSS)).isCloseTo(2400.00, offset(0.001));
    }

    // ── response headers ─────────────────────────────────────────────────────

    @Test
    void responseHeaders_contentTypeAndDisposition_areCorrect() throws Exception {
        saveEmployee(companyA.getId(), "EMP-001", "Vijitha Bandara");
        saveWorkingDays(companyA.getId(), "2026-04", 23);

        mockMvc.perform(get(exportUrl("2026-04"))
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", String.valueOf(companyA.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''Wood%20Lanka%20Payroll%202026-04.xlsx")));
    }
}
