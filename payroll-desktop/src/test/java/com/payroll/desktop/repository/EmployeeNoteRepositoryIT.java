package com.payroll.desktop.repository;

import com.payroll.core.entity.EmployeeNote;
import com.payroll.desktop.db.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeNoteRepositoryIT {

    private static EmployeeNote note(Long employeeId, LocalDate date, String text) {
        EmployeeNote n = new EmployeeNote();
        n.setEmployeeId(employeeId);
        n.setNoteDate(date);
        n.setNoteText(text);
        n.setCreatedBy("admin1");
        n.setCreatedAt(Instant.now());
        return n;
    }

    @Test
    void findByMonthReturnsOnlyNotesInThatMonthAcrossAllEmployees(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeNoteRepository(db.getSessionFactory());

            repo.save(note(1L, LocalDate.of(2026, 6, 30), "June note for emp 1"));
            repo.save(note(2L, LocalDate.of(2026, 7, 1), "July note for emp 2"));
            repo.save(note(1L, LocalDate.of(2026, 7, 15), "July note for emp 1"));
            repo.save(note(3L, LocalDate.of(2026, 8, 1), "August note for emp 3"));

            List<EmployeeNote> julyNotes = repo.findByMonth("2026-07");

            assertThat(julyNotes).hasSize(2);
            assertThat(julyNotes).extracting(EmployeeNote::getNoteText)
                    .containsExactlyInAnyOrder("July note for emp 2", "July note for emp 1");
        }
    }

    @Test
    void findByEmployeeAndMonthFiltersByBothEmployeeAndMonth(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repo = new EmployeeNoteRepository(db.getSessionFactory());

            repo.save(note(1L, LocalDate.of(2026, 7, 1), "July note for emp 1"));
            repo.save(note(1L, LocalDate.of(2026, 6, 30), "June note for emp 1"));
            repo.save(note(2L, LocalDate.of(2026, 7, 5), "July note for emp 2"));

            List<EmployeeNote> result = repo.findByEmployeeAndMonth(1L, "2026-07");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getNoteText()).isEqualTo("July note for emp 1");
        }
    }
}
