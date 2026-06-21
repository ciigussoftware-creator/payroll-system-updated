package com.payroll.core.attendance;

import java.util.ArrayList;
import java.util.List;

public class SessionBuilder {

    public SessionResult buildSessions(List<Scan> scans) {
        List<Session> sessions = new ArrayList<>();
        boolean hasMissingClockOut = false;

        int i = 0;
        while (i < scans.size()) {
            Scan current = scans.get(i);
            if (current.getScanType() == ScanType.ENTRY) {
                int next = i + 1;
                if (next < scans.size() && scans.get(next).getScanType() == ScanType.EXIT) {
                    sessions.add(new Session(current.getScanDatetime(), scans.get(next).getScanDatetime()));
                    i = next + 1;
                } else {
                    hasMissingClockOut = true;
                    i++;
                }
            } else {
                i++;
            }
        }

        return new SessionResult(sessions, hasMissingClockOut);
    }
}
