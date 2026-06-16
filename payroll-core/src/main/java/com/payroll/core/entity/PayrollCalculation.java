package com.payroll.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payroll_calculations")
public class PayrollCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Column(name = "days_worked", nullable = false)
    private int daysWorked;

    @Column(name = "gross_salary", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossSalary;

    @Column(name = "epf_employee_deduction", nullable = false, precision = 19, scale = 4)
    private BigDecimal epfEmployeeDeduction;

    @Column(name = "epf_employer_contribution", nullable = false, precision = 19, scale = 4)
    private BigDecimal epfEmployerContribution;

    @Column(name = "etf_contribution", nullable = false, precision = 19, scale = 4)
    private BigDecimal etfContribution;

    @Column(name = "total_ot_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalOtPay;

    @Column(name = "total_allowances", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAllowances;

    @Column(name = "total_advances_deducted", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAdvancesDeducted;

    @Column(name = "net_take_home", nullable = false, precision = 19, scale = 4)
    private BigDecimal netTakeHome;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "calculated_by", nullable = false)
    private Long calculatedBy;

    public PayrollCalculation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }

    public int getDaysWorked() { return daysWorked; }
    public void setDaysWorked(int daysWorked) { this.daysWorked = daysWorked; }

    public BigDecimal getGrossSalary() { return grossSalary; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }

    public BigDecimal getEpfEmployeeDeduction() { return epfEmployeeDeduction; }
    public void setEpfEmployeeDeduction(BigDecimal epfEmployeeDeduction) { this.epfEmployeeDeduction = epfEmployeeDeduction; }

    public BigDecimal getEpfEmployerContribution() { return epfEmployerContribution; }
    public void setEpfEmployerContribution(BigDecimal epfEmployerContribution) { this.epfEmployerContribution = epfEmployerContribution; }

    public BigDecimal getEtfContribution() { return etfContribution; }
    public void setEtfContribution(BigDecimal etfContribution) { this.etfContribution = etfContribution; }

    public BigDecimal getTotalOtPay() { return totalOtPay; }
    public void setTotalOtPay(BigDecimal totalOtPay) { this.totalOtPay = totalOtPay; }

    public BigDecimal getTotalAllowances() { return totalAllowances; }
    public void setTotalAllowances(BigDecimal totalAllowances) { this.totalAllowances = totalAllowances; }

    public BigDecimal getTotalAdvancesDeducted() { return totalAdvancesDeducted; }
    public void setTotalAdvancesDeducted(BigDecimal totalAdvancesDeducted) { this.totalAdvancesDeducted = totalAdvancesDeducted; }

    public BigDecimal getNetTakeHome() { return netTakeHome; }
    public void setNetTakeHome(BigDecimal netTakeHome) { this.netTakeHome = netTakeHome; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

    public Long getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(Long calculatedBy) { this.calculatedBy = calculatedBy; }
}
