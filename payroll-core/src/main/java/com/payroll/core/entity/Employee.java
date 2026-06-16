package com.payroll.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "rfid_card_id", unique = true)
    private String rfidCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeCategory category;

    @Column(name = "gross_daily_salary", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossDailySalary;

    @Column(name = "epf_employee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal epfEmployeeRate = new BigDecimal("0.08");

    @Column(name = "epf_employer_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal epfEmployerRate = new BigDecimal("0.12");

    @Column(name = "etf_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal etfRate = new BigDecimal("0.03");

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Employee() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRfidCardId() { return rfidCardId; }
    public void setRfidCardId(String rfidCardId) { this.rfidCardId = rfidCardId; }

    public EmployeeCategory getCategory() { return category; }
    public void setCategory(EmployeeCategory category) { this.category = category; }

    public BigDecimal getGrossDailySalary() { return grossDailySalary; }
    public void setGrossDailySalary(BigDecimal grossDailySalary) { this.grossDailySalary = grossDailySalary; }

    public BigDecimal getEpfEmployeeRate() { return epfEmployeeRate; }
    public void setEpfEmployeeRate(BigDecimal epfEmployeeRate) { this.epfEmployeeRate = epfEmployeeRate; }

    public BigDecimal getEpfEmployerRate() { return epfEmployerRate; }
    public void setEpfEmployerRate(BigDecimal epfEmployerRate) { this.epfEmployerRate = epfEmployerRate; }

    public BigDecimal getEtfRate() { return etfRate; }
    public void setEtfRate(BigDecimal etfRate) { this.etfRate = etfRate; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
