package com.payroll.web.calculation;

import com.payroll.core.attendance.AttendanceConstants;
import com.payroll.core.attendance.AttendanceEngine;
import com.payroll.core.attendance.DayInput;
import com.payroll.core.attendance.DayResult;
import com.payroll.core.attendance.Scan;
import com.payroll.core.constants.PayrollConstants;
import com.payroll.core.engine.GrossSalaryEngine;
import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.CloudDayLevelOTConfig;
import com.payroll.core.entity.CloudOtEmployeeAuthorization;
import com.payroll.core.entity.CloudWorkingDaysConfig;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.MonthlyPayrollInput;
import com.payroll.web.repository.AttendanceRecordRepository;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
import com.payroll.web.repository.CloudWorkingDaysConfigRepository;
import com.payroll.web.repository.EmployeeRepository;
import com.payroll.web.repository.MonthlyPayrollInputRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Computes the confidential monthly payroll (OT pay, allowances, advances, take-home) for
 * every active employee of a company, assembling synced cloud data (employees, attendance,
 * OT authorization, working days, monthly payroll inputs) into the shape expected by
 * payroll-core's {@link AttendanceEngine} and {@link GrossSalaryEngine} — both reused
 * unchanged. No persistence: computed fresh on every call, matching the desktop's
 * Statutory Export pattern.
 *
 * <p>Unlike the desktop's StatutoryCalculationService.buildDayInput (which always passes
 * an empty break schedule — a known defect where breaks never get deducted from OT), this
 * builder passes the real break schedule so OT money computed here is correct.
 */
@Service
public class PayrollCalculationService {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private final EmployeeRepository employeeRepository;
    private final CloudWorkingDaysConfigRepository workingDaysRepository;
    private final CloudDayLevelOTConfigRepository dayLevelOTConfigRepository;
    private final CloudOtEmployeeAuthorizationRepository otAuthorizationRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MonthlyPayrollInputRepository monthlyPayrollInputRepository;

    private final AttendanceEngine attendanceEngine = new AttendanceEngine();
    private final GrossSalaryEngine grossSalaryEngine = new GrossSalaryEngine();

    public PayrollCalculationService(
            EmployeeRepository employeeRepository,
            CloudWorkingDaysConfigRepository workingDaysRepository,
            CloudDayLevelOTConfigRepository dayLevelOTConfigRepository,
            CloudOtEmployeeAuthorizationRepository otAuthorizationRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            MonthlyPayrollInputRepository monthlyPayrollInputRepository) {
        this.employeeRepository = employeeRepository;
        this.workingDaysRepository = workingDaysRepository;
        this.dayLevelOTConfigRepository = dayLevelOTConfigRepository;
        this.otAuthorizationRepository = otAuthorizationRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.monthlyPayrollInputRepository = monthlyPayrollInputRepository;
    }

    public List<PayrollCalculationResponse> calculateForMonth(Long companyId, String periodMonth) {
        YearMonth ym = YearMonth.parse(periodMonth);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        Optional<CloudWorkingDaysConfig> workingDaysConfig =
                workingDaysRepository.findByCompanyIdAndPeriodMonth(companyId, periodMonth);
        boolean configured = workingDaysConfig.isPresent();
        int availableWorkingDays = workingDaysConfig.map(CloudWorkingDaysConfig::getAvailableWorkingDays).orElse(0);

        Map<LocalDate, CloudDayLevelOTConfig> dayConfigByDate = dayLevelOTConfigRepository
                .findByCompanyIdAndConfigDateBetween(companyId, monthStart, monthEnd).stream()
                .collect(Collectors.toMap(CloudDayLevelOTConfig::getConfigDate, c -> c));

        Map<LocalDate, List<CloudOtEmployeeAuthorization>> authByDate = otAuthorizationRepository
                .findByCompanyIdAndAuthDateBetween(companyId, monthStart, monthEnd).stream()
                .collect(Collectors.groupingBy(CloudOtEmployeeAuthorization::getAuthDate));

        Map<Long, List<AttendanceRecord>> recordsByEmployeeId = attendanceRecordRepository
                .findByCompanyIdAndScanDatetimeRange(
                        companyId, monthStart.atStartOfDay(), monthEnd.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        Map<String, MonthlyPayrollInput> inputsByEmployeeCode = monthlyPayrollInputRepository
                .findByCompanyIdAndPeriodMonth(companyId, periodMonth).stream()
                .collect(Collectors.toMap(MonthlyPayrollInput::getEmployeeCode, i -> i));

        List<Employee> employees = employeeRepository.findByCompanyIdAndIsActiveTrue(companyId);
        List<PayrollCalculationResponse> results = new ArrayList<>(employees.size());
        for (Employee emp : employees) {
            results.add(computeForEmployee(
                    emp, ym, dayConfigByDate, authByDate,
                    recordsByEmployeeId.getOrDefault(emp.getId(), List.of()),
                    configured, availableWorkingDays,
                    inputsByEmployeeCode.get(emp.getEmployeeCode())));
        }
        return results;
    }

    private PayrollCalculationResponse computeForEmployee(
            Employee emp,
            YearMonth ym,
            Map<LocalDate, CloudDayLevelOTConfig> dayConfigByDate,
            Map<LocalDate, List<CloudOtEmployeeAuthorization>> authByDate,
            List<AttendanceRecord> employeeRecords,
            boolean configured,
            int availableWorkingDays,
            MonthlyPayrollInput input) {

        Map<LocalDate, List<AttendanceRecord>> recordsByDay = employeeRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getScanDatetime().toLocalDate()));

        BigDecimal daysWorked = BigDecimal.ZERO;
        int otMinutesTotal = 0;

        int daysInMonth = ym.lengthOfMonth();
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = ym.atDay(d);
            DayInput dayInput = buildDayInput(
                    emp, date,
                    recordsByDay.getOrDefault(date, List.of()),
                    dayConfigByDate.get(date),
                    authByDate.getOrDefault(date, List.of()));
            DayResult result = attendanceEngine.classifyDay(dayInput);
            daysWorked = daysWorked.add(result.getDayCredit());
            otMinutesTotal += result.getOtMinutes();
        }

        BigDecimal otHours = BigDecimal.valueOf(otMinutesTotal).divide(SIXTY, 2, ROUNDING);

        if (!configured) {
            return new PayrollCalculationResponse(
                    emp.getEmployeeCode(), emp.getName(), 0,
                    daysWorked, otHours,
                    null, null, null, null, null,
                    null, null, null, null,
                    true);
        }

        BigDecimal otPay = otHours.multiply(PayrollConstants.OT_RATE_PER_HOUR).setScale(SCALE, ROUNDING);
        BigDecimal gross = grossSalaryEngine.grossSalary(availableWorkingDays, daysWorked);
        BigDecimal epfEmployee = grossSalaryEngine.epfEmployeeDeduction(gross);
        BigDecimal epfEmployer = grossSalaryEngine.epfEmployerContribution(gross);
        BigDecimal etf = grossSalaryEngine.etfContribution(gross);

        BigDecimal allowance = orZero(input != null ? input.getAllowance() : null);
        BigDecimal salaryAdvance = orZero(input != null ? input.getSalaryAdvance() : null);
        BigDecimal festivalAdvance = orZero(input != null ? input.getFestivalAdvance() : null);

        BigDecimal takeHome = gross.add(otPay).add(allowance)
                .subtract(epfEmployee).subtract(salaryAdvance).subtract(festivalAdvance)
                .setScale(SCALE, ROUNDING);

        return new PayrollCalculationResponse(
                emp.getEmployeeCode(), emp.getName(), availableWorkingDays,
                daysWorked, otHours,
                otPay, gross, epfEmployee, epfEmployer, etf,
                allowance, salaryAdvance, festivalAdvance, takeHome,
                false);
    }

    private DayInput buildDayInput(
            Employee emp, LocalDate date,
            List<AttendanceRecord> records,
            CloudDayLevelOTConfig dayConfig,
            List<CloudOtEmployeeAuthorization> authsForDate) {

        List<Scan> scans = records.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getScanDatetime))
                .map(r -> new Scan(r.getScanDatetime(), toAttScanType(r.getScanType())))
                .collect(Collectors.toList());

        com.payroll.core.attendance.DayType dayType = resolveDayType(date, dayConfig);
        boolean dayOtOn = dayConfig != null && dayConfig.isAllStaffOt();

        boolean anyPerEmployeeSwitchSetToday = !authsForDate.isEmpty();
        Boolean perEmployeeOtAuthorized = authsForDate.stream()
                .filter(a -> a.getEmployeeCode().equals(emp.getEmployeeCode()))
                .findFirst()
                .map(CloudOtEmployeeAuthorization::isAuthorized)
                .orElse(null);

        return new DayInput(
                emp.getCategory(),
                date,
                dayType,
                scans,
                dayOtOn,
                perEmployeeOtAuthorized,
                anyPerEmployeeSwitchSetToday,
                AttendanceConstants.STANDARD_BREAK_SCHEDULE);
    }

    private com.payroll.core.attendance.DayType resolveDayType(LocalDate date, CloudDayLevelOTConfig config) {
        if (config != null) {
            return switch (config.getDayType()) {
                case SUNDAY             -> com.payroll.core.attendance.DayType.SUNDAY;
                case MERCANTILE_HOLIDAY -> com.payroll.core.attendance.DayType.MERCANTILE_HOLIDAY;
                case SPECIAL            -> com.payroll.core.attendance.DayType.SPECIAL;
                case WEEKDAY            -> com.payroll.core.attendance.DayType.WEEKDAY;
            };
        }
        return switch (date.getDayOfWeek()) {
            case SUNDAY   -> com.payroll.core.attendance.DayType.SUNDAY;
            case SATURDAY -> com.payroll.core.attendance.DayType.SATURDAY;
            default       -> com.payroll.core.attendance.DayType.WEEKDAY;
        };
    }

    private com.payroll.core.attendance.ScanType toAttScanType(com.payroll.core.entity.ScanType t) {
        return switch (t) {
            case ENTRY -> com.payroll.core.attendance.ScanType.ENTRY;
            case EXIT  -> com.payroll.core.attendance.ScanType.EXIT;
        };
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
