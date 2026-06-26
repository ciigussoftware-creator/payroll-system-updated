package com.payroll.desktop.repository;

import com.payroll.core.entity.WorkingDaysConfig;
import com.payroll.desktop.db.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingDaysConfigRepositoryIT {

    // ── save + find round trip ─────────────────────────────────────────────────

    @Test
    void saveAndFindByPeriodMonthRoundTrip(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new WorkingDaysConfigRepository(db.getSessionFactory());

            var config = new WorkingDaysConfig();
            config.setPeriodMonth("2026-06");
            config.setAvailableWorkingDays(22);
            config.setUpdatedAt(java.time.Instant.now());
            config.setUpdatedBy("admin");
            repo.save(config);

            var found = repo.findByPeriodMonth("2026-06");
            assertThat(found).isPresent();
            assertThat(found.get().getAvailableWorkingDays()).isEqualTo(22);
            assertThat(found.get().getUpdatedBy()).isEqualTo("admin");
        }
    }

    @Test
    void findByPeriodMonthReturnsEmptyForUnknownPeriod(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new WorkingDaysConfigRepository(db.getSessionFactory());
            assertThat(repo.findByPeriodMonth("2025-01")).isEmpty();
        }
    }

    // ── upsert ────────────────────────────────────────────────────────────────

    @Test
    void upsertCreatesWhenAbsent(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new WorkingDaysConfigRepository(db.getSessionFactory());

            assertThat(repo.findByPeriodMonth("2026-07")).isEmpty();

            repo.upsert("2026-07", 23, "admin");

            var found = repo.findByPeriodMonth("2026-07");
            assertThat(found).isPresent();
            assertThat(found.get().getAvailableWorkingDays()).isEqualTo(23);
            assertThat(found.get().getUpdatedBy()).isEqualTo("admin");
            assertThat(found.get().getUpdatedAt()).isNotNull();
        }
    }

    @Test
    void upsertUpdatesWhenPresent(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new WorkingDaysConfigRepository(db.getSessionFactory());

            repo.upsert("2026-08", 20, "admin");
            Long idAfterInsert = repo.findByPeriodMonth("2026-08").get().getId();

            repo.upsert("2026-08", 21, "superadmin");

            var found = repo.findByPeriodMonth("2026-08");
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(idAfterInsert); // same row, not a duplicate
            assertThat(found.get().getAvailableWorkingDays()).isEqualTo(21);
            assertThat(found.get().getUpdatedBy()).isEqualTo("superadmin");
        }
    }

    @Test
    void upsertDoesNotCreateDuplicateRows(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new WorkingDaysConfigRepository(db.getSessionFactory());

            repo.upsert("2026-09", 18, "admin");
            repo.upsert("2026-09", 19, "admin");
            repo.upsert("2026-09", 20, "admin");

            assertThat(repo.findAll()).hasSize(1);
            assertThat(repo.findByPeriodMonth("2026-09").get().getAvailableWorkingDays()).isEqualTo(20);
        }
    }

    // ── findAll ordering ──────────────────────────────────────────────────────

    @Test
    void findAllIsOrderedByPeriodMonthDescending(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new WorkingDaysConfigRepository(db.getSessionFactory());

            repo.upsert("2026-03", 21, "admin");
            repo.upsert("2025-12", 23, "admin");
            repo.upsert("2026-06", 22, "admin");
            repo.upsert("2026-01", 20, "admin");

            List<WorkingDaysConfig> all = repo.findAll();
            assertThat(all).hasSize(4);
            assertThat(all).extracting(WorkingDaysConfig::getPeriodMonth)
                    .containsExactly("2026-06", "2026-03", "2026-01", "2025-12");
        }
    }
}
