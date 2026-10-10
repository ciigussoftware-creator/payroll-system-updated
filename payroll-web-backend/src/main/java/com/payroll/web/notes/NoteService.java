package com.payroll.web.notes;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.core.entity.CloudEmployeeNote;
import com.payroll.web.repository.AuditLogEntryRepository;
import com.payroll.web.repository.CloudEmployeeNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Super Admin's web note-taking power (Phase 6E). Notes are append-only from this
 * service's perspective — no update/delete — and every note written here also lands
 * in the same audit trail as 6C/6D's corrections, via {@link AuditLogEntry}.
 */
@Service
public class NoteService {

    private final CloudEmployeeNoteRepository noteRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final Clock clock;

    public NoteService(CloudEmployeeNoteRepository noteRepository,
                        AuditLogEntryRepository auditLogEntryRepository,
                        Clock clock) {
        this.noteRepository = noteRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public NoteResponse addNote(NoteRequest request, String username) {
        CloudEmployeeNote note = new CloudEmployeeNote();
        note.setCompanyId(request.companyId());
        note.setEmployeeCode(request.employeeCode());
        note.setNoteDate(request.noteDate());
        note.setNoteText(request.text().strip());
        note.setCreatedBy(username);
        note.setCreatedAt(clock.instant());
        CloudEmployeeNote saved = noteRepository.save(note);

        AuditLogEntry audit = new AuditLogEntry();
        audit.setCompanyId(request.companyId());
        audit.setEntryDatetime(clock.instant());
        audit.setUsername(username);
        audit.setAction("NOTE_ADDED");
        audit.setTargetRef("employee=" + request.employeeCode()
                + ",companyId=" + request.companyId() + ",date=" + request.noteDate());
        audit.setNewValue(preview(note.getNoteText()));
        auditLogEntryRepository.save(audit);

        return NoteResponse.from(saved);
    }

    public List<NoteResponse> findByEmployee(Long companyId, String employeeCode) {
        return noteRepository.findByCompanyIdAndEmployeeCodeOrderByCreatedAtDesc(companyId, employeeCode).stream()
                .map(NoteResponse::from)
                .toList();
    }

    private static String preview(String text) {
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }
}
