package com.payroll.desktop.superadmin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.EmployeeNote;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.EmployeeNoteRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.superadmin.EmployeeNoteService;
import com.payroll.desktop.ui.superadmin.TimestampCorrectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimestampCorrectionIT {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 6, 20, 8, 30);

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static Employee savedEmployee(DatabaseManager db) {
        Employee emp = new Employee();
        emp.setEmployeeCode("TC-001");
        emp.setName("Test Worker");
        emp.setCategory(EmployeeCategory.STANDARD);
        emp.setGrossDailySalary(new BigDecimal("1200.00"));
        new EmployeeRepository(db.getSessionFactory()).save(emp);
        return emp;
    }

    private static AttendanceRecord savedScan(DatabaseManager db, Employee emp, LocalDateTime when,
                                               ScanType type) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(emp);
        r.setScanDatetime(when);
        r.setScanType(type);
        return new AttendanceRecordRepository(db.getSessionFactory()).save(r);
    }

    private static Repos repos(DatabaseManager db) {
        var sf = db.getSessionFactory();
        return new Repos(
                new AttendanceRecordRepository(sf),
                new AuditLogRepository(sf),
                new EmployeeNoteRepository(sf));
    }

    private record Repos(AttendanceRecordRepository attendanceRepo,
                         AuditLogRepository auditLogRepo,
                         EmployeeNoteRepository noteRepo) {
        TimestampCorrectionService correctionService() {
            return new TimestampCorrectionService(attendanceRepo, auditLogRepo);
        }
        EmployeeNoteService noteService() {
            return new EmployeeNoteService(noteRepo, auditLogRepo);
        }
    }

    // ── TIMESTAMP CORRECTION: edit scan ──────────────────────────────────────────

    @Test
    void editScan_preservesOriginal_setsNote_marksUnsynced_writesAudit(@TempDir Path tempDir)
            throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            Employee emp = savedEmployee(db);
            AttendanceRecord scan = savedScan(db, emp, BASE_TIME, ScanType.ENTRY);

            LocalDateTime correctedTime = BASE_TIME.plusMinutes(10);
            repos.correctionService().correctScan(scan.getId(), correctedTime, ScanType.ENTRY,
                    "Clock was slow", "superadmin");

            AttendanceRecord updated = repos.attendanceRepo.findById(scan.getId()).orElseThrow();
            assertThat(updated.getScanDatetime()).isEqualTo(correctedTime);
            assertThat(updated.getOriginalScanDatetime()).isEqualTo(BASE_TIME);
            assertThat(updated.getCorrectionNote()).isEqualTo("Clock was slow");
            assertThat(updated.isSyncedToCloud()).isFalse();

            List<AuditLogEntry> audits = repos.auditLogRepo.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getAction()).isEqualTo("TIMESTAMP_CORRECTED");
            assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
            assertThat(audits.get(0).getReason()).isEqualTo("Clock was slow");
            assertThat(audits.get(0).getOldValue()).contains(BASE_TIME.toString());
            assertThat(audits.get(0).getNewValue()).contains(correctedTime.toString());
        }
    }

    // ── TIMESTAMP CORRECTION: edit twice — original preserved ─────────────────────

    @Test
    void editTwice_originalScanDatetimeIsNeverOverwritten(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            Employee emp = savedEmployee(db);
            AttendanceRecord scan = savedScan(db, emp, BASE_TIME, ScanType.ENTRY);
            var svc = repos.correctionService();

            LocalDateTime edit1 = BASE_TIME.plusMinutes(5);
            LocalDateTime edit2 = BASE_TIME.plusMinutes(15);

            svc.correctScan(scan.getId(), edit1, ScanType.ENTRY, "First fix", "superadmin");
            svc.correctScan(scan.getId(), edit2, ScanType.EXIT,  "Second fix", "superadmin");

            AttendanceRecord updated = repos.attendanceRepo.findById(scan.getId()).orElseThrow();
            // scanDatetime should reflect the second edit
            assertThat(updated.getScanDatetime()).isEqualTo(edit2);
            assertThat(updated.getScanType()).isEqualTo(ScanType.EXIT);
            // originalScanDatetime must still be the TRUE original (BASE_TIME), not edit1
            assertThat(updated.getOriginalScanDatetime()).isEqualTo(BASE_TIME);
        }
    }

    // ── TIMESTAMP CORRECTION: empty reason rejected ───────────────────────────────

    @Test
    void editScan_emptyReason_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            Employee emp = savedEmployee(db);
            AttendanceRecord scan = savedScan(db, emp, BASE_TIME, ScanType.ENTRY);
            var svc = repos.correctionService();

            assertThatThrownBy(() ->
                    svc.correctScan(scan.getId(), BASE_TIME.plusMinutes(5), ScanType.ENTRY, "", "superadmin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");

            assertThatThrownBy(() ->
                    svc.correctScan(scan.getId(), BASE_TIME.plusMinutes(5), ScanType.ENTRY, null, "superadmin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    // ── ADD MISSING SCAN ──────────────────────────────────────────────────────────

    @Test
    void addMissingScan_createsRecord_marksUnsynced_hasSyncUuid_writesAudit(@TempDir Path tempDir)
            throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            Employee emp = savedEmployee(db);
            LocalDateTime scanTime = LocalDateTime.of(2026, 6, 20, 17, 45);

            AttendanceRecord added = repos.correctionService()
                    .addMissingScan(emp, scanTime, ScanType.EXIT, "Forgot to clock out", "superadmin");

            assertThat(added.getId()).isNotNull();
            assertThat(added.getScanDatetime()).isEqualTo(scanTime);
            assertThat(added.getScanType()).isEqualTo(ScanType.EXIT);
            assertThat(added.isSyncedToCloud()).isFalse();
            assertThat(added.getSyncUuid()).isNotNull().isNotBlank();
            assertThat(added.getCorrectionNote()).isEqualTo("Forgot to clock out");
            assertThat(added.getOriginalScanDatetime()).isNull();

            List<AuditLogEntry> audits = repos.auditLogRepo.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getAction()).isEqualTo("SCAN_ADDED");
            assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
            assertThat(audits.get(0).getReason()).isEqualTo("Forgot to clock out");
        }
    }

    @Test
    void addMissingScan_emptyReason_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            Employee emp = savedEmployee(db);
            var svc = repos.correctionService();

            assertThatThrownBy(() ->
                    svc.addMissingScan(emp, BASE_TIME, ScanType.ENTRY, "", "superadmin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    // ── EMPLOYEE NOTE: save + findByEmployeeAndDate round-trip ───────────────────

    @Test
    void employeeNote_saveAndFindByEmployeeAndDate_roundTrip_writesAudit(@TempDir Path tempDir)
            throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            LocalDate noteDate = LocalDate.of(2026, 6, 20);

            EmployeeNote saved = repos.noteService()
                    .addNote(42L, noteDate, "Late arrival — traffic incident", "superadmin");

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getEmployeeId()).isEqualTo(42L);
            assertThat(saved.getNoteDate()).isEqualTo(noteDate);
            assertThat(saved.getNoteText()).isEqualTo("Late arrival — traffic incident");
            assertThat(saved.getCreatedBy()).isEqualTo("superadmin");
            assertThat(saved.getCreatedAt()).isNotNull();

            List<EmployeeNote> notes = repos.noteRepo.findByEmployeeAndDate(42L, noteDate);
            assertThat(notes).hasSize(1);
            assertThat(notes.get(0).getNoteText()).isEqualTo("Late arrival — traffic incident");

            List<AuditLogEntry> audits = repos.auditLogRepo.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getAction()).isEqualTo("NOTE_ADDED");
            assertThat(audits.get(0).getUsername()).isEqualTo("superadmin");
            assertThat(audits.get(0).getTargetRef()).contains("42").contains(noteDate.toString());
        }
    }

    @Test
    void employeeNote_findByEmployee_returnsAllNotesNewestFirst(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var repos = repos(db);
            var svc = repos.noteService();
            LocalDate d1 = LocalDate.of(2026, 6, 18);
            LocalDate d2 = LocalDate.of(2026, 6, 19);

            svc.addNote(10L, d1, "Note A", "superadmin");
            svc.addNote(10L, d2, "Note B", "superadmin");

            List<EmployeeNote> notes = repos.noteRepo.findByEmployee(10L);
            assertThat(notes).hasSize(2);
            // findByEmployee orders by createdAt DESC — Note B (later) should be first
            assertThat(notes.get(0).getNoteText()).isEqualTo("Note B");
        }
    }

    @Test
    void employeeNote_emptyText_rejected(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var svc = repos(db).noteService();
            assertThatThrownBy(() -> svc.addNote(1L, LocalDate.now(), "", "superadmin"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> svc.addNote(1L, LocalDate.now(), "  ", "superadmin"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
