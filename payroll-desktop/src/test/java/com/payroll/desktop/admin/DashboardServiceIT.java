package com.payroll.desktop.admin;

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
import com.payroll.desktop.ui.admin.DashboardService;
import com.payroll.desktop.ui.admin.DashboardSnapshot;
import com.payroll.desktop.ui.admin.EmployeeNoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceIT {

    private static final LocalDate TODAY      = LocalDate.now();
    private static final LocalDate YESTERDAY  = TODAY.minusDays(1);
    private static final LocalDate TWO_DAYS_AGO = TODAY.minusDays(2);

    private static Employee savedEmployee(EmployeeRepository repo, String code) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setName("Worker " + code);
        e.setRfidCardId("RFID-" + code);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1500.00"));
        return repo.save(e);
    }

    private static void scan(AttendanceRecordRepository repo, Employee emp, LocalDate date, int hour, ScanType type) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(emp);
        r.setScanDatetime(LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)));
        r.setScanType(type);
        repo.save(r);
    }

    // ── past-day scans filter ────────────────────────────────────────────────────

    @Test
    void loadForPastDateReturnsOnlyThatDaysScans(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var empRepo = new EmployeeRepository(db.getSessionFactory());
            var attendanceRepo = new AttendanceRecordRepository(db.getSessionFactory());
            var service = new DashboardService(attendanceRepo, empRepo);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            scan(attendanceRepo, emp, TWO_DAYS_AGO, 8, ScanType.ENTRY);
            scan(attendanceRepo, emp, YESTERDAY, 8, ScanType.ENTRY);
            scan(attendanceRepo, emp, YESTERDAY, 17, ScanType.EXIT);
            scan(attendanceRepo, emp, TODAY, 9, ScanType.ENTRY);

            DashboardSnapshot snapshot = service.loadFor(YESTERDAY);

            assertThat(snapshot.date()).isEqualTo(YESTERDAY);
            assertThat(snapshot.scans()).hasSize(2);
            assertThat(snapshot.scans()).allSatisfy(r ->
                    assertThat(r.getScanDatetime().toLocalDate()).isEqualTo(YESTERDAY));
        }
    }

    // ── missing clock-out / currently-in logic ──────────────────────────────────

    @Test
    void lastScanIsEntryIdentifiesMissingClockOutsForPastDate(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var empRepo = new EmployeeRepository(db.getSessionFactory());
            var attendanceRepo = new AttendanceRecordRepository(db.getSessionFactory());
            var service = new DashboardService(attendanceRepo, empRepo);

            Employee neverClockedOut = savedEmployee(empRepo, "EMP-001");
            scan(attendanceRepo, neverClockedOut, YESTERDAY, 8, ScanType.ENTRY);

            Employee clockedOut = savedEmployee(empRepo, "EMP-002");
            scan(attendanceRepo, clockedOut, YESTERDAY, 8, ScanType.ENTRY);
            scan(attendanceRepo, clockedOut, YESTERDAY, 17, ScanType.EXIT);

            Employee reEntered = savedEmployee(empRepo, "EMP-003");
            scan(attendanceRepo, reEntered, YESTERDAY, 8, ScanType.ENTRY);
            scan(attendanceRepo, reEntered, YESTERDAY, 12, ScanType.EXIT);
            scan(attendanceRepo, reEntered, YESTERDAY, 13, ScanType.ENTRY);

            DashboardSnapshot snapshot = service.loadFor(YESTERDAY);

            assertThat(snapshot.lastScanIsEntry())
                    .extracting(r -> r.getEmployee().getEmployeeCode())
                    .containsExactlyInAnyOrder("EMP-001", "EMP-003");
            assertThat(snapshot.scannedEmployeeCount()).isEqualTo(3);
        }
    }

    // ── add note from a scan row ────────────────────────────────────────────────

    @Test
    void addingNoteFromScanRowCreatesNoteForEmployeeAndDateWithAudit(@TempDir Path tempDir) throws IOException {
        try (var db = new DatabaseManager(tempDir)) {
            var empRepo = new EmployeeRepository(db.getSessionFactory());
            var attendanceRepo = new AttendanceRecordRepository(db.getSessionFactory());
            var noteRepo = new EmployeeNoteRepository(db.getSessionFactory());
            var auditRepo = new AuditLogRepository(db.getSessionFactory());
            var noteService = new EmployeeNoteService(noteRepo, auditRepo);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            scan(attendanceRepo, emp, YESTERDAY, 8, ScanType.ENTRY);
            AttendanceRecord scanRow = attendanceRepo.findByDateRange(YESTERDAY, YESTERDAY).get(0);

            // What DashboardScreen's "Add Note" dialog does: derive employee + date from the row.
            noteService.addNote(scanRow.getEmployee().getId(), scanRow.getScanDatetime().toLocalDate(),
                    "Left ID badge at home", "admin1");

            List<EmployeeNote> notes = noteRepo.findByEmployeeAndDate(emp.getId(), YESTERDAY);
            assertThat(notes).hasSize(1);
            assertThat(notes.get(0).getNoteText()).isEqualTo("Left ID badge at home");
            assertThat(notes.get(0).getCreatedBy()).isEqualTo("admin1");

            List<AuditLogEntry> audits = auditRepo.findAll();
            assertThat(audits).anySatisfy(a -> {
                assertThat(a.getAction()).isEqualTo("NOTE_ADDED");
                assertThat(a.getUsername()).isEqualTo("admin1");
                assertThat(a.getTargetRef()).contains(emp.getId().toString()).contains(YESTERDAY.toString());
            });
        }
    }
}
