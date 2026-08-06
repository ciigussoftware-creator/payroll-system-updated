package com.payroll.web.payrollinput;

import com.payroll.core.entity.MonthlyPayrollInput;
import com.payroll.web.repository.MonthlyPayrollInputRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
public class MonthlyPayrollInputService {

    private final MonthlyPayrollInputRepository repository;
    private final Clock clock;

    public MonthlyPayrollInputService(MonthlyPayrollInputRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<MonthlyPayrollInputResponse> findByCompanyAndMonth(Long companyId, String periodMonth) {
        return repository.findByCompanyIdAndPeriodMonth(companyId, periodMonth).stream()
                .map(MonthlyPayrollInputResponse::from)
                .toList();
    }

    public MonthlyPayrollInputResponse upsert(Long companyId, String employeeCode, String periodMonth,
                                               MonthlyPayrollInputRequest request, String updatedBy) {
        Optional<MonthlyPayrollInput> existing =
                repository.findByCompanyIdAndEmployeeCodeAndPeriodMonth(companyId, employeeCode, periodMonth);

        MonthlyPayrollInput input = existing.orElseGet(MonthlyPayrollInput::new);
        input.setCompanyId(companyId);
        input.setEmployeeCode(employeeCode);
        input.setPeriodMonth(periodMonth);
        input.setAllowance(orZero(request.allowance()));
        input.setSalaryAdvance(orZero(request.salaryAdvance()));
        input.setFestivalAdvance(orZero(request.festivalAdvance()));
        input.setUpdatedBy(updatedBy);
        input.setUpdatedAt(clock.instant());

        return MonthlyPayrollInputResponse.from(repository.save(input));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
