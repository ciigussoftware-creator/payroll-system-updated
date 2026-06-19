package com.payroll.core.attendance;

import java.util.List;

public class SessionResult {

    private final List<Session> sessions;
    private final boolean hasMissingClockOut;

    public SessionResult(List<Session> sessions, boolean hasMissingClockOut) {
        this.sessions = List.copyOf(sessions);
        this.hasMissingClockOut = hasMissingClockOut;
    }

    public List<Session> getSessions() { return sessions; }
    public boolean hasMissingClockOut() { return hasMissingClockOut; }
}
