package com.payroll.core.attendance;

import java.time.LocalTime;

public class BreakPeriod {

    private final LocalTime start;
    private final LocalTime end;
    private final String label;

    public BreakPeriod(LocalTime start, LocalTime end, String label) {
        this.start = start;
        this.end = end;
        this.label = label;
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    public String getLabel() { return label; }
}
