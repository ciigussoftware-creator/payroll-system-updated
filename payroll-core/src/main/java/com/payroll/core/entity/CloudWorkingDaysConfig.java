package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Cloud-side, company-scoped mirror of the desktop's {@link WorkingDaysConfig}.
 * Unlike the desktop entity (single-tenant, unique on periodMonth alone), this
 * is uniqued on (companyId, periodMonth) so multiple factories can each set
 * their own working-days count for the same calendar month.
 */
@Entity
@Table(name = "cloud_working_days_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "period_month"}))
public class CloudWorkingDaysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "period_month", nullable = false)
    private String periodMonth;

    @Column(name = "available_working_days", nullable = false)
    private int availableWorkingDays;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CloudWorkingDaysConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }

    public int getAvailableWorkingDays() { return availableWorkingDays; }
    public void setAvailableWorkingDays(int availableWorkingDays) { this.availableWorkingDays = availableWorkingDays; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
