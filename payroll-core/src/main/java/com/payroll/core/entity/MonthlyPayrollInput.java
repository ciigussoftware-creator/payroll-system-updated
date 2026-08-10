package com.payroll.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cloud-only, company-scoped monthly payroll input entered directly by the Super Admin
 * via the web app (no desktop screen or sync path). Feeds the confidential payroll
 * calculation for a given employee and month.
 */
@Entity
@Table(name = "monthly_payroll_inputs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "employee_code", "period_month"}))
public class MonthlyPayrollInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "period_month", nullable = false)
    private String periodMonth;

    @Column(name = "allowance", nullable = false, precision = 19, scale = 4)
    private BigDecimal allowance = BigDecimal.ZERO;

    @Column(name = "salary_advance", nullable = false, precision = 19, scale = 4)
    private BigDecimal salaryAdvance = BigDecimal.ZERO;

    @Column(name = "festival_advance", nullable = false, precision = 19, scale = 4)
    private BigDecimal festivalAdvance = BigDecimal.ZERO;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MonthlyPayrollInput() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }

    public BigDecimal getAllowance() { return allowance; }
    public void setAllowance(BigDecimal allowance) { this.allowance = allowance; }

    public BigDecimal getSalaryAdvance() { return salaryAdvance; }
    public void setSalaryAdvance(BigDecimal salaryAdvance) { this.salaryAdvance = salaryAdvance; }

    public BigDecimal getFestivalAdvance() { return festivalAdvance; }
    public void setFestivalAdvance(BigDecimal festivalAdvance) { this.festivalAdvance = festivalAdvance; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
