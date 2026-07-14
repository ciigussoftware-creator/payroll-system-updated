package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log_entries")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_datetime", nullable = false)
    private Instant entryDatetime;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_ref", nullable = false)
    private String targetRef;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "reason", length = 2000)
    private String reason;

    public AuditLogEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getEntryDatetime() { return entryDatetime; }
    public void setEntryDatetime(Instant entryDatetime) { this.entryDatetime = entryDatetime; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
