package com.payroll.web.repository;

import com.payroll.core.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findBySyncUuid(String syncUuid);

    @Query("SELECT r FROM AttendanceRecord r JOIN FETCH r.employee " +
            "WHERE r.employee.companyId = :companyId " +
            "AND r.scanDatetime >= :fromDt AND r.scanDatetime < :toDt " +
            "ORDER BY r.scanDatetime")
    List<AttendanceRecord> findByCompanyIdAndScanDatetimeRange(
            @Param("companyId") Long companyId,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt);

    @Query("SELECT r FROM AttendanceRecord r JOIN FETCH r.employee " +
            "WHERE r.employee.id = :employeeId " +
            "AND r.scanDatetime >= :fromDt AND r.scanDatetime < :toDt " +
            "ORDER BY r.scanDatetime")
    List<AttendanceRecord> findByEmployeeIdAndScanDatetimeRange(
            @Param("employeeId") Long employeeId,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt);
}
