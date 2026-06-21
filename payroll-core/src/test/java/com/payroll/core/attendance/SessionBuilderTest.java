package com.payroll.core.attendance;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBuilderTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 8, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 1, 13, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 1, 1, 17, 0);

    private final SessionBuilder builder = new SessionBuilder();

    private static Scan entry(LocalDateTime t) { return new Scan(t, ScanType.ENTRY); }
    private static Scan exit(LocalDateTime t)  { return new Scan(t, ScanType.EXIT); }

    @Test
    void singleEntryExitPair_producesOneSession() {
        SessionResult result = builder.buildSessions(List.of(entry(T0), exit(T1)));

        assertThat(result.getSessions()).hasSize(1);
        assertThat(result.getSessions().get(0).getStart()).isEqualTo(T0);
        assertThat(result.getSessions().get(0).getEnd()).isEqualTo(T1);
        assertThat(result.hasMissingClockOut()).isFalse();
    }

    @Test
    void twoEntryExitPairs_producesTwoSessions() {
        SessionResult result = builder.buildSessions(List.of(entry(T0), exit(T1), entry(T2), exit(T3)));

        assertThat(result.getSessions()).hasSize(2);
        assertThat(result.getSessions().get(0).getStart()).isEqualTo(T0);
        assertThat(result.getSessions().get(0).getEnd()).isEqualTo(T1);
        assertThat(result.getSessions().get(1).getStart()).isEqualTo(T2);
        assertThat(result.getSessions().get(1).getEnd()).isEqualTo(T3);
        assertThat(result.hasMissingClockOut()).isFalse();
    }

    @Test
    void entryWithNoExit_flagsMissingClockOut_noCompletedSessions() {
        SessionResult result = builder.buildSessions(List.of(entry(T0)));

        assertThat(result.getSessions()).isEmpty();
        assertThat(result.hasMissingClockOut()).isTrue();
    }

    @Test
    void onePairThenDanglingEntry_oneSessionAndMissingFlag() {
        SessionResult result = builder.buildSessions(List.of(entry(T0), exit(T1), entry(T2)));

        assertThat(result.getSessions()).hasSize(1);
        assertThat(result.getSessions().get(0).getStart()).isEqualTo(T0);
        assertThat(result.getSessions().get(0).getEnd()).isEqualTo(T1);
        assertThat(result.hasMissingClockOut()).isTrue();
    }

    @Test
    void zeroScans_emptySessionsNoMissingFlag() {
        SessionResult result = builder.buildSessions(List.of());

        assertThat(result.getSessions()).isEmpty();
        assertThat(result.hasMissingClockOut()).isFalse();
    }
}
