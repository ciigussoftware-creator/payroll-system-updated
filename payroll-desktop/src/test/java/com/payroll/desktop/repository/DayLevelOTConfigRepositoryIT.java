package com.payroll.desktop.repository;

import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.DayType;
import com.payroll.desktop.db.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DayLevelOTConfigRepositoryIT {

    private static DayLevelOTConfig newConfig(LocalDate date, DayType dayType, boolean allStaffOt) {
        DayLevelOTConfig c = new DayLevelOTConfig();
        c.setConfigDate(date);
        c.setDayType(dayType);
        c.setAllStaffOt(allStaffOt);
        c.setSetBy(1L);
        c.setSetAt(Instant.now());
        return c;
    }

    @Test
    void saveAndFindByDateRoundTrip(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            DayLevelOTConfigRepository repo = new DayLevelOTConfigRepository(db.getSessionFactory());

            LocalDate date = LocalDate.of(2026, 6, 20);
            repo.save(newConfig(date, DayType.WEEKDAY, true));

            Optional<DayLevelOTConfig> found = repo.findByDate(date);
            assertThat(found).isPresent();
            assertThat(found.get().getDayType()).isEqualTo(DayType.WEEKDAY);
            assertThat(found.get().isAllStaffOt()).isTrue();
        }
    }

    @Test
    void updateChangesIsAllStaffOtFlag(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            DayLevelOTConfigRepository repo = new DayLevelOTConfigRepository(db.getSessionFactory());

            LocalDate date = LocalDate.of(2026, 6, 20);
            DayLevelOTConfig saved = repo.save(newConfig(date, DayType.WEEKDAY, false));

            saved.setAllStaffOt(true);
            repo.update(saved);

            Optional<DayLevelOTConfig> found = repo.findByDate(date);
            assertThat(found).isPresent();
            assertThat(found.get().isAllStaffOt()).isTrue();
        }
    }

    @Test
    void deleteByDateRemovesConfig(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            DayLevelOTConfigRepository repo = new DayLevelOTConfigRepository(db.getSessionFactory());

            LocalDate date = LocalDate.of(2026, 6, 20);
            repo.save(newConfig(date, DayType.SUNDAY, false));

            repo.deleteByDate(date);

            assertThat(repo.findByDate(date)).isEmpty();
        }
    }

    @Test
    void findByDateRangeReturnsCorrectRange(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            DayLevelOTConfigRepository repo = new DayLevelOTConfigRepository(db.getSessionFactory());

            repo.save(newConfig(LocalDate.of(2026, 6, 18), DayType.WEEKDAY, false));
            repo.save(newConfig(LocalDate.of(2026, 6, 19), DayType.WEEKDAY, false));
            repo.save(newConfig(LocalDate.of(2026, 6, 20), DayType.WEEKDAY, true));
            repo.save(newConfig(LocalDate.of(2026, 6, 21), DayType.SUNDAY, false));
            repo.save(newConfig(LocalDate.of(2026, 6, 22), DayType.WEEKDAY, false));

            List<DayLevelOTConfig> range = repo.findByDateRange(
                    LocalDate.of(2026, 6, 19),
                    LocalDate.of(2026, 6, 21));

            assertThat(range).hasSize(3);
            assertThat(range).extracting(DayLevelOTConfig::getConfigDate)
                    .containsExactly(
                            LocalDate.of(2026, 6, 19),
                            LocalDate.of(2026, 6, 20),
                            LocalDate.of(2026, 6, 21));
        }
    }
}
