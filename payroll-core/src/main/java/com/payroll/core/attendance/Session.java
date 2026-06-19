package com.payroll.core.attendance;

import java.time.LocalDateTime;

public class Session {

    private final LocalDateTime start;
    private final LocalDateTime end;

    public Session(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
}
