package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "working_days_config")
public class WorkingDaysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_month", nullable = false, unique = true)
    private String periodMonth; // "YYYY-MM", e.g. "2026-04"

    @Column(name = "available_working_days", nullable = false)
    private int availableWorkingDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    public WorkingDaysConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }

    public int getAvailableWorkingDays() { return availableWorkingDays; }
    public void setAvailableWorkingDays(int availableWorkingDays) { this.availableWorkingDays = availableWorkingDays; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
