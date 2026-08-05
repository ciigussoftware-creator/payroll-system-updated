package com.payroll.web.sync;

import com.payroll.core.entity.Employee;
import com.payroll.web.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies a full-push batch of desktop employees to the cloud store, scoped to the
 * company resolved from the caller's API key. Upserts by (companyId, employeeCode).
 * Each record is validated and persisted independently via {@link EmployeeRepository}
 * (individually transactional, default Spring Data JPA behaviour), so one bad record
 * — e.g. a duplicate rfidCardId — cannot block the rest of the batch.
 */
@Service
public class EmployeeSyncService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSyncService.class);

    private final EmployeeRepository employeeRepository;

    public EmployeeSyncService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public List<EmployeeSyncResult> processBatch(Long companyId, List<EmployeeSyncRequest> requests) {
        List<EmployeeSyncResult> results = new ArrayList<>(requests.size());
        for (EmployeeSyncRequest request : requests) {
            results.add(processOne(companyId, request));
        }
        return results;
    }

    private EmployeeSyncResult processOne(Long companyId, EmployeeSyncRequest request) {
        try {
            Optional<Employee> existing =
                    employeeRepository.findByEmployeeCodeAndCompanyId(request.employeeCode(), companyId);

            if (existing.isEmpty()) {
                Employee employee = new Employee();
                employee.setCompanyId(companyId);
                employee.setEmployeeCode(request.employeeCode());
                applyIncoming(employee, request);
                employeeRepository.save(employee);
                return EmployeeSyncResult.accepted(request.employeeCode());
            }

            Employee current = existing.get();
            applyIncoming(current, request);
            employeeRepository.save(current);
            return EmployeeSyncResult.updated(request.employeeCode());
        } catch (Exception e) {
            log.warn("Rejecting employee sync record employeeCode={}: {}", request.employeeCode(), e.getMessage());
            return EmployeeSyncResult.rejected(request.employeeCode(), "Processing error: " + e.getMessage());
        }
    }

    private void applyIncoming(Employee employee, EmployeeSyncRequest request) {
        employee.setName(request.name());
        employee.setRfidCardId(request.rfidCardId());
        employee.setCategory(request.category());
        employee.setGrossDailySalary(request.grossDailySalary());
        employee.setEpfEmployeeRate(request.epfEmployeeRate());
        employee.setEpfEmployerRate(request.epfEmployerRate());
        employee.setEtfRate(request.etfRate());
        employee.setActive(request.isActive());
    }
}
