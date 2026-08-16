package com.payroll.web.auditlog;

import com.payroll.core.entity.AuditLogEntry;

import java.time.Instant;

public record AuditLogEntryResponse(
        Long id,
        Instant entryDatetime,
        String username,
        String action,
        String targetRef,
        String oldValue,
        String newValue,
        String reason
) {
    static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.getId(), entry.getEntryDatetime(), entry.getUsername(), entry.getAction(),
                entry.getTargetRef(), entry.getOldValue(), entry.getNewValue(), entry.getReason());
    }
}
