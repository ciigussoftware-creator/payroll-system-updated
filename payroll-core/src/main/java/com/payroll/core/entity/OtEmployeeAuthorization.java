package com.payroll.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ot_employee_authorizations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "auth_date"}))
public class OtEmployeeAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "auth_date", nullable = false)
    private LocalDate date;

    @Column(name = "authorized", nullable = false)
    private boolean authorized;

    @Column(name = "set_by", nullable = false)
    private String setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    public OtEmployeeAuthorization() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public boolean isAuthorized() { return authorized; }
    public void setAuthorized(boolean authorized) { this.authorized = authorized; }

    public String getSetBy() { return setBy; }
    public void setSetBy(String setBy) { this.setBy = setBy; }

    public Instant getSetAt() { return setAt; }
    public void setSetAt(Instant setAt) { this.setAt = setAt; }
}
