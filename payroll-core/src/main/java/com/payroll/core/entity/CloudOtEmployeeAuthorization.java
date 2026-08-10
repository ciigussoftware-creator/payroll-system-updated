package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cloud-side, company-scoped mirror of the desktop's {@link OtEmployeeAuthorization}.
 * Stores the employeeCode directly (rather than a numeric employeeId or FK) since the
 * cloud only ever learns of an employee through the sync API's employeeCode, and the
 * unique key needs to be scoped per company rather than per desktop-local employee id.
 */
@Entity
@Table(name = "cloud_ot_employee_authorizations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "employee_code", "auth_date"}))
public class CloudOtEmployeeAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "auth_date", nullable = false)
    private LocalDate authDate;

    @Column(name = "authorized", nullable = false)
    private boolean authorized;

    @Column(name = "set_by")
    private String setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    public CloudOtEmployeeAuthorization() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public LocalDate getAuthDate() { return authDate; }
    public void setAuthDate(LocalDate authDate) { this.authDate = authDate; }

    public boolean isAuthorized() { return authorized; }
    public void setAuthorized(boolean authorized) { this.authorized = authorized; }

    public String getSetBy() { return setBy; }
    public void setSetBy(String setBy) { this.setBy = setBy; }

    public Instant getSetAt() { return setAt; }
    public void setSetAt(Instant setAt) { this.setAt = setAt; }
}
