package com.payroll.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "statutory_overrides",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "period_month"}))
public class StatutoryOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "period_month", nullable = false)
    private String periodMonth;

    @Column(name = "overridden_days_worked", nullable = false, precision = 10, scale = 4)
    private BigDecimal overriddenDaysWorked;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    public StatutoryOverride() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }

    public BigDecimal getOverriddenDaysWorked() { return overriddenDaysWorked; }
    public void setOverriddenDaysWorked(BigDecimal d) { this.overriddenDaysWorked = d; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
