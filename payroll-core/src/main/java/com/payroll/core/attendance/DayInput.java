package com.payroll.core.attendance;

import com.payroll.core.entity.EmployeeCategory;

import java.time.LocalDate;
import java.util.List;

public class DayInput {

    private final EmployeeCategory category;
    private final LocalDate date;
    private final DayType dayType;
    private final List<Scan> scans;
    private final boolean dayLevelOtOn;
    private final Boolean perEmployeeOtAuthorized;
    private final boolean anyPerEmployeeSwitchSetToday;
    private final List<BreakPeriod> breakSchedule;

    public DayInput(
            EmployeeCategory category,
            LocalDate date,
            DayType dayType,
            List<Scan> scans,
            boolean dayLevelOtOn,
            Boolean perEmployeeOtAuthorized,
            boolean anyPerEmployeeSwitchSetToday,
            List<BreakPeriod> breakSchedule) {
        this.category = category;
        this.date = date;
        this.dayType = dayType;
        this.scans = scans;
        this.dayLevelOtOn = dayLevelOtOn;
        this.perEmployeeOtAuthorized = perEmployeeOtAuthorized;
        this.anyPerEmployeeSwitchSetToday = anyPerEmployeeSwitchSetToday;
        this.breakSchedule = breakSchedule;
    }

    public EmployeeCategory getCategory() { return category; }
    public LocalDate getDate() { return date; }
    public DayType getDayType() { return dayType; }
    public List<Scan> getScans() { return scans; }
    public boolean isDayLevelOtOn() { return dayLevelOtOn; }
    public Boolean getPerEmployeeOtAuthorized() { return perEmployeeOtAuthorized; }
    public boolean isAnyPerEmployeeSwitchSetToday() { return anyPerEmployeeSwitchSetToday; }
    public List<BreakPeriod> getBreakSchedule() { return breakSchedule; }
}
