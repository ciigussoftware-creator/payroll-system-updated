package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "day_level_ot_configs")
public class DayLevelOTConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_date", nullable = false, unique = true)
    private LocalDate configDate;

    @Column(name = "is_all_staff_ot", nullable = false)
    private boolean isAllStaffOt = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;

    @Column(name = "set_by", nullable = false)
    private Long setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    @Column(name = "notes")
    private String notes;

    public DayLevelOTConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getConfigDate() { return configDate; }
    public void setConfigDate(LocalDate configDate) { this.configDate = configDate; }

    public boolean isAllStaffOt() { return isAllStaffOt; }
    public void setAllStaffOt(boolean allStaffOt) { isAllStaffOt = allStaffOt; }

    public DayType getDayType() { return dayType; }
    public void setDayType(DayType dayType) { this.dayType = dayType; }

    public Long getSetBy() { return setBy; }
    public void setSetBy(Long setBy) { this.setBy = setBy; }

    public Instant getSetAt() { return setAt; }
    public void setSetAt(Instant setAt) { this.setAt = setAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
