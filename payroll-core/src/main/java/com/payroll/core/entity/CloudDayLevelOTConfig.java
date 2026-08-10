package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cloud-side, company-scoped mirror of the desktop's {@link DayLevelOTConfig}.
 * Unlike the desktop entity (single-tenant, unique on configDate alone), this
 * is uniqued on (companyId, configDate) so multiple factories can each set
 * their own OT policy for the same calendar date.
 */
@Entity
@Table(name = "cloud_day_level_ot_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "config_date"}))
public class CloudDayLevelOTConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "config_date", nullable = false)
    private LocalDate configDate;

    @Column(name = "is_all_staff_ot", nullable = false)
    private boolean allStaffOt;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;

    @Column(name = "set_by")
    private Long setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    public CloudDayLevelOTConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public LocalDate getConfigDate() { return configDate; }
    public void setConfigDate(LocalDate configDate) { this.configDate = configDate; }

    public boolean isAllStaffOt() { return allStaffOt; }
    public void setAllStaffOt(boolean allStaffOt) { this.allStaffOt = allStaffOt; }

    public DayType getDayType() { return dayType; }
    public void setDayType(DayType dayType) { this.dayType = dayType; }

    public Long getSetBy() { return setBy; }
    public void setSetBy(Long setBy) { this.setBy = setBy; }

    public Instant getSetAt() { return setAt; }
    public void setSetAt(Instant setAt) { this.setAt = setAt; }
}
