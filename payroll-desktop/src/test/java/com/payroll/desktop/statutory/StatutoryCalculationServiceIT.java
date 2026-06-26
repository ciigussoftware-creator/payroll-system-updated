package com.payroll.desktop.statutory;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatutoryCalculationServiceIT {

    // ── golden case ───────────────────────────────────────────────────────────

    @Test
    void goldenCase_18of23Days_gross24000_epf1920_balance22080(@TempDir Path tempDir)
            throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-001", "Vijitha Bandara");
            repos.workingDays.upsert("2026-04", 23, "admin");

            // 18 full weekdays in April 2026 (08:00 entry, 17:00 exit = 9h = FULL_DAY)
            for (LocalDate day : fullWorkDays18()) {
                repos.saveAttendance(emp, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
            }

            List<StatutoryRow> rows = repos.service().computeForMonth("2026-04");

            assertThat(rows).hasSize(1);
            StatutoryRow row = rows.get(0);
            assertThat(row.employeeCode()).isEqualTo("EMP-001");
            assertThat(row.availableWorkingDays()).isEqualTo(23);
            assertThat(row.computedDaysWorked()).isEqualByComparingTo("18");
            assertThat(row.effectiveDaysWorked()).isEqualByComparingTo("18");
            assertThat(row.gross()).isEqualByComparingTo("24000");
            assertThat(row.epfEmployee()).isEqualByComparingTo("1920");
            assertThat(row.epfEmployer()).isEqualByComparingTo("2880");
            assertThat(row.etf()).isEqualByComparingTo("720");
            assertThat(row.adminBalance()).isEqualByComparingTo("22080");
            assertThat(row.flags()).isEmpty();
            assertThat(row.overrideReason()).isNull();
        }
    }

    // ── working days not configured ───────────────────────────────────────────

    @Test
    void workingDaysNotSet_rowFlaggedAndGrossBlank(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            repos.saveEmployee("EMP-002", "Test Worker");
            // No WorkingDaysConfig for 2026-05

            List<StatutoryRow> rows = repos.service().computeForMonth("2026-05");

            assertThat(rows).hasSize(1);
            StatutoryRow row = rows.get(0);
            assertThat(row.hasFlag(StatutoryFlag.WORKING_DAYS_NOT_SET)).isTrue();
            assertThat(row.gross()).isNull();
            assertThat(row.epfEmployee()).isNull();
            assertThat(row.adminBalance()).isNull();
        }
    }

    // ── override applied ──────────────────────────────────────────────────────

    @Test
    void overrideApplied_effectiveDaysAndGrossReflectOverride_computedStillPresent(
            @TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-003", "Override Worker");
            repos.workingDays.upsert("2026-04", 23, "admin");

            // 10 full work days (computed = 10, gross without override = 30000 - 13*1200 = 14400)
            List<LocalDate> tenDays = fullWorkDays18().subList(0, 10);
            for (LocalDate day : tenDays) {
                repos.saveAttendance(emp, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
            }

            // Apply override: effectiveDays = 18 (same as golden case)
            repos.overrides.upsert(emp.getId(), "2026-04",
                    new BigDecimal("18"), "Approved special exception", "admin");

            List<StatutoryRow> rows = repos.service().computeForMonth("2026-04");

            assertThat(rows).hasSize(1);
            StatutoryRow row = rows.get(0);
            assertThat(row.computedDaysWorked()).isEqualByComparingTo("10");  // unchanged
            assertThat(row.effectiveDaysWorked()).isEqualByComparingTo("18"); // override
            assertThat(row.gross()).isEqualByComparingTo("24000");            // based on override
            assertThat(row.overrideReason()).isEqualTo("Approved special exception");
            assertThat(row.flags()).isEmpty();
        }
    }

    // ── override with empty reason rejected ───────────────────────────────────

    @Test
    void overrideWithEmptyReason_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-004", "Worker");

            assertThatThrownBy(() ->
                    repos.overrides.upsert(emp.getId(), "2026-04",
                            new BigDecimal("18"), "", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("required");

            assertThatThrownBy(() ->
                    repos.overrides.upsert(emp.getId(), "2026-04",
                            new BigDecimal("18"), "   ", "admin"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── fractional days end-to-end ────────────────────────────────────────────

    @Test
    void halfDayScans_produceCorrectFractionalGross(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-HLF", "Kamal");
            repos.workingDays.upsert("2026-05", 22, "admin");

            // One HALF_DAY_AM in May 2026: entry 08:00, exit 12:15 (4h15m = AM_HALF_DURATION)
            repos.saveAttendance(emp,
                    LocalDate.of(2026, 5, 4),  // Monday
                    LocalTime.of(8, 0), LocalTime.of(12, 15));

            List<StatutoryRow> rows = repos.service().computeForMonth("2026-05");

            assertThat(rows).hasSize(1);
            StatutoryRow row = rows.get(0);
            assertThat(row.computedDaysWorked()).isEqualByComparingTo("0.5");
            assertThat(row.effectiveDaysWorked()).isEqualByComparingTo("0.5");
            assertThat(row.gross()).isEqualByComparingTo("4200");      // 30000 - 21.5×1200
            assertThat(row.epfEmployee()).isEqualByComparingTo("336"); // 4200 × 0.08
            assertThat(row.adminBalance()).isEqualByComparingTo("3864"); // 4200 - 336
        }
    }

    @Test
    void fractionalMix_18point5days_gross24600(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-FRC", "Fractional Worker");
            repos.workingDays.upsert("2026-04", 23, "admin");

            // 18 full days (Apr 1–24 weekdays, same as golden) + 1 half day
            for (LocalDate day : fullWorkDays18()) {
                repos.saveAttendance(emp, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
            }
            // Add a half-day on Apr 27 (Monday)
            repos.saveAttendance(emp,
                    LocalDate.of(2026, 4, 27),
                    LocalTime.of(8, 0), LocalTime.of(12, 15));

            List<StatutoryRow> rows = repos.service().computeForMonth("2026-04");

            assertThat(rows).hasSize(1);
            StatutoryRow row = rows.get(0);
            assertThat(row.computedDaysWorked()).isEqualByComparingTo("18.5");
            assertThat(row.gross()).isEqualByComparingTo("24600"); // 30000 - 4.5×1200
        }
    }

    // ── excel export ──────────────────────────────────────────────────────────

    @Test
    void excelExport_statutoryOnlyHeaders_goldenRowValuesCorrect(@TempDir Path tempDir)
            throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = new Repos(db);
            Employee emp = repos.saveEmployee("EMP-001", "Vijitha Bandara");
            repos.workingDays.upsert("2026-04", 23, "admin");

            for (LocalDate day : fullWorkDays18()) {
                repos.saveAttendance(emp, day, LocalTime.of(8, 0), LocalTime.of(17, 0));
            }

            List<StatutoryRow> rows = repos.service().computeForMonth("2026-04");
            Path xlsxFile = tempDir.resolve("statutory_2026-04.xlsx");
            new StatutoryExcelExporter().export(rows, "2026-04", xlsxFile);

            try (Workbook wb = WorkbookFactory.create(xlsxFile.toFile())) {
                Sheet sheet = wb.getSheetAt(0);
                Row headerRow = sheet.getRow(0);

                List<String> headers = new ArrayList<>();
                headerRow.forEach(cell -> headers.add(cell.getStringCellValue()));

                // Statutory-only columns present
                assertThat(headers).contains(
                        "Employee Code", "Name", "Available Days",
                        "Computed Days", "Effective Days",
                        "Gross", "EPF 8%", "EPF 12%", "ETF 3%", "Admin Balance");

                // No OT / allowance / take-home columns
                assertThat(headers).noneMatch(h ->
                        h.toLowerCase().contains("ot") ||
                        h.toLowerCase().contains("overtime") ||
                        h.toLowerCase().contains("allowance") ||
                        h.toLowerCase().contains("take home") ||
                        h.toLowerCase().contains("takehome"));

                // Check data row values
                Row dataRow = sheet.getRow(1);
                int grossIdx   = headers.indexOf("Gross");
                int epf8Idx    = headers.indexOf("EPF 8%");
                int balanceIdx = headers.indexOf("Admin Balance");

                assertThat(dataRow.getCell(grossIdx).getNumericCellValue()).isEqualTo(24000.0);
                assertThat(dataRow.getCell(epf8Idx).getNumericCellValue()).isEqualTo(1920.0);
                assertThat(dataRow.getCell(balanceIdx).getNumericCellValue()).isEqualTo(22080.0);
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** 18 full weekdays in April 2026 (April 1 = Wednesday). */
    private static List<LocalDate> fullWorkDays18() {
        return List.of(
                LocalDate.of(2026, 4,  1), LocalDate.of(2026, 4,  2), LocalDate.of(2026, 4,  3),
                LocalDate.of(2026, 4,  6), LocalDate.of(2026, 4,  7), LocalDate.of(2026, 4,  8),
                LocalDate.of(2026, 4,  9), LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 4, 16), LocalDate.of(2026, 4, 17),
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 21), LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 4, 23), LocalDate.of(2026, 4, 24));
    }

    private static class Repos {
        final AttendanceRecordRepository attendance;
        final EmployeeRepository employees;
        final WorkingDaysConfigRepository workingDays;
        final DayLevelOTConfigRepository dayLevelOT;
        final StatutoryOverrideRepository overrides;

        Repos(DatabaseManager db) {
            attendance  = new AttendanceRecordRepository(db.getSessionFactory());
            employees   = new EmployeeRepository(db.getSessionFactory());
            workingDays = new WorkingDaysConfigRepository(db.getSessionFactory());
            dayLevelOT  = new DayLevelOTConfigRepository(db.getSessionFactory());
            overrides   = new StatutoryOverrideRepository(db.getSessionFactory());
        }

        StatutoryCalculationService service() {
            return new StatutoryCalculationService(
                    attendance, employees, workingDays, dayLevelOT, overrides);
        }

        Employee saveEmployee(String code, String name) {
            Employee e = new Employee();
            e.setEmployeeCode(code);
            e.setName(name);
            e.setCategory(EmployeeCategory.STANDARD);
            e.setGrossDailySalary(new BigDecimal("1200.00"));
            e.setActive(true);
            return employees.save(e);
        }

        void saveAttendance(Employee emp, LocalDate date, LocalTime entryTime, LocalTime exitTime) {
            AttendanceRecord entry = new AttendanceRecord();
            entry.setEmployee(emp);
            entry.setScanDatetime(LocalDateTime.of(date, entryTime));
            entry.setScanType(ScanType.ENTRY);
            attendance.save(entry);

            AttendanceRecord exit = new AttendanceRecord();
            exit.setEmployee(emp);
            exit.setScanDatetime(LocalDateTime.of(date, exitTime));
            exit.setScanType(ScanType.EXIT);
            attendance.save(exit);
        }
    }
}
