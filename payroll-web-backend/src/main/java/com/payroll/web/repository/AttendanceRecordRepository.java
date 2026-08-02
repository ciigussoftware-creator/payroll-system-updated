package com.payroll.web.repository;

import com.payroll.core.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findBySyncUuid(String syncUuid);
}
