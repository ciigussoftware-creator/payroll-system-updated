package com.payroll.web.notes;

import com.payroll.core.entity.CloudEmployeeNote;

import java.time.Instant;
import java.time.LocalDate;

public record NoteResponse(
        Long id,
        String employeeCode,
        LocalDate noteDate,
        String text,
        String createdBy,
        Instant createdAt
) {
    static NoteResponse from(CloudEmployeeNote note) {
        return new NoteResponse(
                note.getId(), note.getEmployeeCode(), note.getNoteDate(),
                note.getNoteText(), note.getCreatedBy(), note.getCreatedAt());
    }
}
