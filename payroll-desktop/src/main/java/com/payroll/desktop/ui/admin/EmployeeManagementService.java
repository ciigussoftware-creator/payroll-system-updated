package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.Employee;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.EmployeeRepository;

import java.time.Instant;

/** Reactivate / hard-delete logic for the Employees screen, with audit logging. */
public class EmployeeManagementService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AuditLogRepository auditLogRepository;

    public EmployeeManagementService(EmployeeRepository employeeRepository,
                                     AttendanceRecordRepository attendanceRepository,
                                     AuditLogRepository auditLogRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public void reactivate(Employee employee, String username) {
        employee.setActive(true);
        employeeRepository.update(employee);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(username);
        audit.setAction("EMPLOYEE_REACTIVATED");
        audit.setTargetRef(employee.getEmployeeCode());
        auditLogRepository.save(audit);
    }

    /**
     * Permanently deletes an employee and all dependent records. Writes the audit entry
     * BEFORE deleting so there is a surviving record that the deletion happened, even
     * though the employee row itself is gone afterward.
     */
    public void deleteEmployee(Employee employee, String username) {
        long attendanceCount = attendanceRepository.countByEmployee(employee.getId());

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(username);
        audit.setAction("EMPLOYEE_DELETED");
        audit.setTargetRef(employee.getEmployeeCode() + " " + employee.getName());
        audit.setOldValue("attendanceRecords=" + attendanceCount);
        auditLogRepository.save(audit);

        employeeRepository.deleteById(employee.getId());
    }
}
