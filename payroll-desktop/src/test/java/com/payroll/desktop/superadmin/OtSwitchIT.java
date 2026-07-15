package com.payroll.desktop.superadmin;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.DayType;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;
import com.payroll.desktop.ui.superadmin.OtSwitchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtSwitchIT {

    private static final LocalDate FUTURE = LocalDate.now().plusDays(1);
    private static final LocalDate PAST   = LocalDate.now().minusDays(1);

    private static LocalDate nextSunday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private static LocalDate nextWeekday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    // ── AuditLogEntry: save + findAll returns it; insert-only ─────────────────────

    @Test
    void auditLogEntry_saveAndFindAll_returnsEntry(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var auditRepo = new AuditLogRepository(db.getSessionFactory());

            AuditLogEntry entry = auditEntry("OT_DAYLEVEL_SET", "date=2026-06-26", Instant.now());
            auditRepo.save(entry);

            List<AuditLogEntry> all = auditRepo.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getId()).isNotNull();
            assertThat(all.get(0).getAction()).isEqualTo("OT_DAYLEVEL_SET");
            assertThat(all.get(0).getUsername()).isEqualTo("superadmin");
            assertThat(all.get(0).getTargetRef()).isEqualTo("date=2026-06-26");
        }
    }

    @Test
    void auditLogRepository_isInsertOnly_noUpdateOrDelete(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var auditRepo = new AuditLogRepository(db.getSessionFactory());
            auditRepo.save(auditEntry("ACTION_A", "ref=1", Instant.now()));
            auditRepo.save(auditEntry("ACTION_B", "ref=2", Instant.now().plusSeconds(5)));

            assertThat(auditRepo.findAll()).hasSize(2);
        }
    }

    // ── AuditLogRepository ordering: newest first ──────────────────────────────────

    @Test
    void auditLogRepository_orderedNewestFirst(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var auditRepo = new AuditLogRepository(db.getSessionFactory());

            Instant t1 = Instant.ofEpochSecond(1_000_000);
            Instant t2 = Instant.ofEpochSecond(2_000_000);
            Instant t3 = Instant.ofEpochSecond(3_000_000);

            auditRepo.save(auditEntry("A", "ref=1", t1));
            auditRepo.save(auditEntry("B", "ref=2", t2));
            auditRepo.save(auditEntry("C", "ref=3", t3));

            List<AuditLogEntry> all = auditRepo.findAll();
            assertThat(all).hasSize(3);
            assertThat(all.get(0).getTargetRef()).isEqualTo("ref=3"); // newest first
            assertThat(all.get(1).getTargetRef()).isEqualTo("ref=2");
            assertThat(all.get(2).getTargetRef()).isEqualTo("ref=1");
        }
    }

    // ── Day-level upsert writes config + audit entry ───────────────────────────────

    @Test
    void dayLevelUpsert_writesConfigAndAudit(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            repos.service().saveDayLevel(FUTURE, DayType.WEEKDAY, true, "superadmin", null);

            var config = repos.dayLevelRepo.findByDate(FUTURE);
            assertThat(config).isPresent();
            assertThat(config.get().getDayType()).isEqualTo(DayType.WEEKDAY);
            assertThat(config.get().isAllStaffOt()).isTrue();

            List<AuditLogEntry> audits = repos.auditRepo.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getAction()).isEqualTo("OT_DAYLEVEL_SET");
            assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
            assertThat(audits.get(0).getNewValue()).isEqualTo("WEEKDAY/true");
            assertThat(audits.get(0).getOldValue()).isNull();
        }
    }

    @Test
    void dayLevelUpsert_secondSave_updatesAndRecordsOldValue(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var service = repos.service();
            service.saveDayLevel(FUTURE, DayType.WEEKDAY, false, "superadmin", null);
            service.saveDayLevel(FUTURE, DayType.SUNDAY, true, "superadmin", null);

            var config = repos.dayLevelRepo.findByDate(FUTURE);
            assertThat(config.get().getDayType()).isEqualTo(DayType.SUNDAY);

            List<AuditLogEntry> audits = repos.auditRepo.findAll(); // newest first
            assertThat(audits).hasSize(2);
            assertThat(audits.get(0).getOldValue()).isEqualTo("WEEKDAY/false");
            assertThat(audits.get(0).getNewValue()).isEqualTo("SUNDAY/true");
        }
    }

    // ── Per-employee authorization save writes rows + audit entries ────────────────

    @Test
    void employeeAuth_writesRowsAndAuditEntries(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var service = repos.service();

            service.saveEmployeeAuthorization(FUTURE, 1L, true, "EMP-001", "superadmin", null);
            service.saveEmployeeAuthorization(FUTURE, 2L, true, "EMP-002", "superadmin", null);

            assertThat(repos.otAuthRepo.findByDate(FUTURE)).hasSize(2);

            List<AuditLogEntry> audits = repos.auditRepo.findAll();
            assertThat(audits).hasSize(2);
            assertThat(audits).allMatch(a -> "OT_EMPLOYEE_SET".equals(a.getAction()));
            assertThat(audits).allMatch(a -> "false".equals(a.getOldValue()));
            assertThat(audits).allMatch(a -> "true".equals(a.getNewValue()));
        }
    }

    @Test
    void employeeAuth_noChangeSkipsWriteAndAudit(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var service = repos.service();

            // Save authorized=true, then save again with authorized=true → no change
            service.saveEmployeeAuthorization(FUTURE, 1L, true, "EMP-001", "superadmin", null);
            service.saveEmployeeAuthorization(FUTURE, 1L, true, "EMP-001", "superadmin", null);

            // Only one audit entry (the first change from false→true)
            assertThat(repos.auditRepo.findAll()).hasSize(1);
        }
    }

    // ── Retroactive: empty reason → rejected ──────────────────────────────────────

    @Test
    void retroactive_emptyReason_dayLevel_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var service = repos(db).service();

            assertThatThrownBy(() ->
                    service.saveDayLevel(PAST, DayType.WEEKDAY, true, "superadmin", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");

            assertThatThrownBy(() ->
                    service.saveDayLevel(PAST, DayType.WEEKDAY, true, "superadmin", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    @Test
    void retroactive_emptyReason_employeeAuth_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var service = repos(db).service();

            assertThatThrownBy(() ->
                    service.saveEmployeeAuthorization(PAST, 1L, true, "EMP-001", "superadmin", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");

            assertThatThrownBy(() ->
                    service.saveEmployeeAuthorization(PAST, 1L, true, "EMP-001", "superadmin", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    // ── Retroactive: with reason → saved + reason in audit ────────────────────────

    @Test
    void retroactive_withReason_dayLevel_savedAndReasonInAudit(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            String reason = "Correction approved by manager";

            repos.service().saveDayLevel(PAST, DayType.MERCANTILE_HOLIDAY, false, "superadmin", reason);

            assertThat(repos.dayLevelRepo.findByDate(PAST)).isPresent();

            List<AuditLogEntry> audits = repos.auditRepo.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getReason()).isEqualTo(reason);
        }
    }

    @Test
    void retroactive_withReason_employeeAuth_savedAndReasonInAudit(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            String reason = "OT approved retroactively";

            repos.service().saveEmployeeAuthorization(PAST, 5L, true, "EMP-005", "superadmin", reason);

            assertThat(repos.otAuthRepo.findByEmployeeAndDate(5L, PAST)).isPresent();

            List<AuditLogEntry> audits = repos.auditRepo.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getReason()).isEqualTo(reason);
            assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
        }
    }

    // ── saveAll: combined single-button save ────────────────────────────────────────

    @Test
    void saveAll_persistsDayLevelAndEmployeeAuthsInOneOperation_withAudits(@TempDir Path tempDir)
            throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var service = repos.service();

            var employees = List.of(
                    new OtSwitchService.EmployeeAuth(1L, "EMP-001", true),
                    new OtSwitchService.EmployeeAuth(2L, "EMP-002", false));

            service.saveAll(FUTURE, true, false, employees, "superadmin", null);

            var config = repos.dayLevelRepo.findByDate(FUTURE);
            assertThat(config).isPresent();
            assertThat(config.get().isAllStaffOt()).isTrue();
            assertThat(config.get().getDayType()).isEqualTo(OtSwitchService.deriveDayType(FUTURE));

            assertThat(repos.otAuthRepo.findByEmployeeAndDate(1L, FUTURE)).isPresent();
            assertThat(repos.otAuthRepo.findByEmployeeAndDate(1L, FUTURE).get().isAuthorized()).isTrue();
            // EMP-002 authorized=false matches the default (no prior row) so no auth row/audit is written for it
            assertThat(repos.otAuthRepo.findByEmployeeAndDate(2L, FUTURE)).isEmpty();

            List<AuditLogEntry> audits = repos.auditRepo.findAll();
            assertThat(audits).anyMatch(a -> "OT_DAYLEVEL_SET".equals(a.getAction()));
            assertThat(audits).anyMatch(a -> "OT_EMPLOYEE_SET".equals(a.getAction())
                    && ("employee=EMP-001 date=" + FUTURE).equals(a.getTargetRef()));
        }
    }

    @Test
    void saveAll_retroactiveWithEmptyReason_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var service = repos(db).service();
            var employees = List.of(new OtSwitchService.EmployeeAuth(1L, "EMP-001", true));

            assertThatThrownBy(() -> service.saveAll(PAST, true, false, employees, "superadmin", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");

            assertThatThrownBy(() -> service.saveAll(PAST, true, false, employees, "superadmin", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    @Test
    void saveAll_retroactiveWithReason_savedAndReasonInAudits(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var employees = List.of(new OtSwitchService.EmployeeAuth(1L, "EMP-001", true));
            String reason = "Backdated correction approved";

            repos.service().saveAll(PAST, true, false, employees, "superadmin", reason);

            assertThat(repos.dayLevelRepo.findByDate(PAST)).isPresent();
            List<AuditLogEntry> audits = repos.auditRepo.findAll();
            assertThat(audits).hasSize(2);
            assertThat(audits).allMatch(a -> reason.equals(a.getReason()));
        }
    }

    // ── holiday flag: mercantile holiday override ───────────────────────────────────

    @Test
    void saveAll_holidayTicked_storesMercantileHolidayDayType(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            LocalDate weekday = nextWeekday();

            repos.service().saveAll(weekday, true, true, List.of(), "superadmin", null);

            var config = repos.dayLevelRepo.findByDate(weekday);
            assertThat(config).isPresent();
            assertThat(config.get().getDayType()).isEqualTo(DayType.MERCANTILE_HOLIDAY);
        }
    }

    @Test
    void saveAll_holidayUnticked_storesDerivedDayType(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            LocalDate sunday  = nextSunday();
            LocalDate weekday = nextWeekday();

            repos.service().saveAll(sunday, true, false, List.of(), "superadmin", null);
            repos.service().saveAll(weekday, true, false, List.of(), "superadmin", null);

            assertThat(repos.dayLevelRepo.findByDate(sunday).get().getDayType()).isEqualTo(DayType.SUNDAY);
            assertThat(repos.dayLevelRepo.findByDate(weekday).get().getDayType()).isEqualTo(DayType.WEEKDAY);
        }
    }

    @Test
    void loadDayConfig_afterHolidaySave_reflectsMercantileHoliday(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var service = repos.service();
            LocalDate weekday = nextWeekday();

            service.saveAll(weekday, true, true, List.of(), "superadmin", null);

            // What OtSwitchScreen.loadDayLevelConfig ticks the checkbox from
            var loaded = service.loadDayConfig(weekday);
            assertThat(loaded).isPresent();
            assertThat(loaded.get().getDayType()).isEqualTo(DayType.MERCANTILE_HOLIDAY);
        }
    }

    @Test
    void saveAll_retroactiveHolidayWithEmptyReason_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var service = repos(db).service();

            assertThatThrownBy(() -> service.saveAll(PAST, true, true, List.of(), "superadmin", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");

            assertThatThrownBy(() -> service.saveAll(PAST, true, true, List.of(), "superadmin", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    // ── findByDateRange ────────────────────────────────────────────────────────────

    @Test
    void auditLog_findByDateRange_filtersCorrectly(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var auditRepo = new AuditLogRepository(db.getSessionFactory());

            Instant base = Instant.ofEpochSecond(1_000_000);
            auditRepo.save(auditEntry("A", "ref=1", base));
            auditRepo.save(auditEntry("B", "ref=2", base.plusSeconds(100)));
            auditRepo.save(auditEntry("C", "ref=3", base.plusSeconds(200)));

            List<AuditLogEntry> inRange = auditRepo.findByDateRange(
                    base.plusSeconds(50), base.plusSeconds(150));
            assertThat(inRange).hasSize(1);
            assertThat(inRange.get(0).getTargetRef()).isEqualTo("ref=2");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static Repos repos(DatabaseManager db) {
        var sf = db.getSessionFactory();
        return new Repos(
                new DayLevelOTConfigRepository(sf),
                new OtEmployeeAuthorizationRepository(sf),
                new AuditLogRepository(sf));
    }

    private static AuditLogEntry auditEntry(String action, String targetRef, Instant when) {
        AuditLogEntry e = new AuditLogEntry();
        e.setEntryDatetime(when);
        e.setUsername("superadmin");
        e.setAction(action);
        e.setTargetRef(targetRef);
        return e;
    }

    private record Repos(
            DayLevelOTConfigRepository dayLevelRepo,
            OtEmployeeAuthorizationRepository otAuthRepo,
            AuditLogRepository auditRepo) {
        OtSwitchService service() {
            return new OtSwitchService(dayLevelRepo, otAuthRepo, auditRepo);
        }
    }
}
