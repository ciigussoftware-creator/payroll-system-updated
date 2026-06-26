package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.EmployeeNote;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.EmployeeNoteRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class EmployeeNoteService {

    private final EmployeeNoteRepository noteRepo;
    private final AuditLogRepository auditLogRepo;

    public EmployeeNoteService(EmployeeNoteRepository noteRepo, AuditLogRepository auditLogRepo) {
        this.noteRepo = noteRepo;
        this.auditLogRepo = auditLogRepo;
    }

    public EmployeeNote addNote(Long employeeId, LocalDate noteDate, String noteText,
                                String createdBy) {
        if (noteText == null || noteText.isBlank()) {
            throw new IllegalArgumentException("Note text cannot be empty");
        }

        EmployeeNote note = new EmployeeNote();
        note.setEmployeeId(employeeId);
        note.setNoteDate(noteDate);
        note.setNoteText(noteText.strip());
        note.setCreatedBy(createdBy);
        note.setCreatedAt(Instant.now());
        EmployeeNote saved = noteRepo.save(note);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setEntryDatetime(Instant.now());
        audit.setUsername(createdBy);
        audit.setAction("NOTE_ADDED");
        audit.setTargetRef("employee=" + employeeId + ",date=" + noteDate);
        String preview = noteText.length() > 120 ? noteText.substring(0, 120) + "…" : noteText;
        audit.setNewValue(preview);
        auditLogRepo.save(audit);

        return saved;
    }

    public List<EmployeeNote> findByEmployee(Long employeeId) {
        return noteRepo.findByEmployee(employeeId);
    }

    public List<EmployeeNote> findByEmployeeAndDate(Long employeeId, LocalDate date) {
        return noteRepo.findByEmployeeAndDate(employeeId, date);
    }
}
