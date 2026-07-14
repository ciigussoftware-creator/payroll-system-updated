package com.payroll.desktop.statutory;

import com.payroll.core.attendance.AttendanceEngine;
import com.payroll.core.attendance.BreakPeriod;
import com.payroll.core.attendance.DayInput;
import com.payroll.core.attendance.DayResult;
import com.payroll.core.attendance.Scan;
import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.StatutoryOverride;
import com.payroll.core.engine.GrossSalaryEngine;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StatutoryCalculationService {

    private final AttendanceRecordRepository attendanceRepo;
    private final EmployeeRepository employeeRepo;
    private final WorkingDaysConfigRepository workingDaysRepo;
    private final DayLevelOTConfigRepository dayLevelOTRepo;
    private final StatutoryOverrideRepository overrideRepo;
    private final AttendanceEngine attendanceEngine = new AttendanceEngine();
    private final GrossSalaryEngine salaryEngine = new GrossSalaryEngine();

    public StatutoryCalculationService(
            AttendanceRecordRepository attendanceRepo,
            EmployeeRepository employeeRepo,
            WorkingDaysConfigRepository workingDaysRepo,
            DayLevelOTConfigRepository dayLevelOTRepo,
            StatutoryOverrideRepository overrideRepo) {
        this.attendanceRepo = attendanceRepo;
        this.employeeRepo = employeeRepo;
        this.workingDaysRepo = workingDaysRepo;
        this.dayLevelOTRepo = dayLevelOTRepo;
        this.overrideRepo = overrideRepo;
    }

    public List<StatutoryRow> computeForMonth(String periodMonth) {
        YearMonth ym = YearMonth.parse(periodMonth);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        var workingDaysConfig = workingDaysRepo.findByPeriodMonth(periodMonth);
        boolean configured = workingDaysConfig.isPresent();
        int availableDays = workingDaysConfig.map(c -> c.getAvailableWorkingDays()).orElse(0);

        // Batch-load all day-level OT configs to avoid N+1
        Map<LocalDate, DayLevelOTConfig> dayConfigMap = dayLevelOTRepo
                .findByDateRange(monthStart, monthEnd).stream()
                .collect(Collectors.toMap(DayLevelOTConfig::getConfigDate, c -> c));

        // Batch-load all attendance records for the month
        Map<Long, List<AttendanceRecord>> recordsByEmployee = attendanceRepo
                .findByDateRange(monthStart, monthEnd).stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        // Batch-load all overrides for the month
        Map<Long, StatutoryOverride> overridesByEmployee = overrideRepo
                .findByMonth(periodMonth).stream()
                .collect(Collectors.toMap(StatutoryOverride::getEmployeeId, o -> o));

        List<Employee> employees = employeeRepo.findAllActive();
        List<StatutoryRow> rows = new ArrayList<>(employees.size());
        for (Employee emp : employees) {
            rows.add(computeRow(
                    emp, ym, dayConfigMap,
                    recordsByEmployee.getOrDefault(emp.getId(), List.of()),
                    configured, availableDays,
                    overridesByEmployee.get(emp.getId())));
        }
        return rows;
    }

    private StatutoryRow computeRow(
            Employee emp,
            YearMonth ym,
            Map<LocalDate, DayLevelOTConfig> dayConfigMap,
            List<AttendanceRecord> records,
            boolean configured,
            int availableDays,
            StatutoryOverride override) {

        Map<LocalDate, List<AttendanceRecord>> byDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getScanDatetime().toLocalDate()));

        BigDecimal daysWorked = BigDecimal.ZERO;
        int daysInMonth = ym.lengthOfMonth();
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = ym.atDay(d);
            DayInput input = buildDayInput(
                    emp, date,
                    byDay.getOrDefault(date, List.of()),
                    dayConfigMap.get(date));
            DayResult result = attendanceEngine.classifyDay(input);
            daysWorked = daysWorked.add(result.getDayCredit());
        }

        BigDecimal effectiveDays;
        String overrideReason;
        if (override != null) {
            effectiveDays = override.getOverriddenDaysWorked();
            overrideReason = override.getReason();
        } else {
            effectiveDays = daysWorked;
            overrideReason = null;
        }

        if (!configured) {
            return new StatutoryRow(
                    emp.getId(), emp.getEmployeeCode(), emp.getName(),
                    0, daysWorked, effectiveDays,
                    null, null, null, null, null,
                    overrideReason, Set.of(StatutoryFlag.WORKING_DAYS_NOT_SET));
        }

        BigDecimal gross = salaryEngine.grossSalary(availableDays, effectiveDays);
        BigDecimal epfEmp = salaryEngine.epfEmployeeDeduction(gross);
        BigDecimal epfEr = salaryEngine.epfEmployerContribution(gross);
        BigDecimal etf = salaryEngine.etfContribution(gross);
        BigDecimal balance = salaryEngine.adminBalance(gross);

        return new StatutoryRow(
                emp.getId(), emp.getEmployeeCode(), emp.getName(),
                availableDays, daysWorked, effectiveDays,
                gross, epfEmp, epfEr, etf, balance,
                overrideReason, Set.of());
    }

    private DayInput buildDayInput(Employee emp, LocalDate date,
                                    List<AttendanceRecord> records,
                                    DayLevelOTConfig dayConfig) {
        List<Scan> scans = records.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getScanDatetime))
                .map(r -> new Scan(r.getScanDatetime(), toAttScanType(r.getScanType())))
                .collect(Collectors.toList());

        com.payroll.core.attendance.DayType dayType = resolveDayType(date, dayConfig);
        boolean dayOtOn = dayConfig != null && dayConfig.isAllStaffOt();

        return new DayInput(
                emp.getCategory(),
                date,
                dayType,
                scans,
                dayOtOn,
                null,     // per-employee OT not tracked in desktop
                false,    // no per-employee OT switch
                List.of() // break schedule not needed for day-credit calc
        );
    }

    private com.payroll.core.attendance.DayType resolveDayType(
            LocalDate date, DayLevelOTConfig config) {
        if (config != null) {
            return switch (config.getDayType()) {
                case SUNDAY            -> com.payroll.core.attendance.DayType.SUNDAY;
                case MERCANTILE_HOLIDAY -> com.payroll.core.attendance.DayType.MERCANTILE_HOLIDAY;
                case SPECIAL           -> com.payroll.core.attendance.DayType.SPECIAL;
                case WEEKDAY           -> com.payroll.core.attendance.DayType.WEEKDAY;
            };
        }
        return switch (date.getDayOfWeek()) {
            case SUNDAY   -> com.payroll.core.attendance.DayType.SUNDAY;
            case SATURDAY -> com.payroll.core.attendance.DayType.SATURDAY;
            default       -> com.payroll.core.attendance.DayType.WEEKDAY;
        };
    }

    private com.payroll.core.attendance.ScanType toAttScanType(
            com.payroll.core.entity.ScanType t) {
        return switch (t) {
            case ENTRY -> com.payroll.core.attendance.ScanType.ENTRY;
            case EXIT  -> com.payroll.core.attendance.ScanType.EXIT;
        };
    }
}
