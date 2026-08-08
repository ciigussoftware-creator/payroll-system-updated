package com.payroll.web.repository;

import com.payroll.core.entity.CloudDayLevelOTConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CloudDayLevelOTConfigRepository extends JpaRepository<CloudDayLevelOTConfig, Long> {
    Optional<CloudDayLevelOTConfig> findByCompanyIdAndConfigDate(Long companyId, LocalDate configDate);

    List<CloudDayLevelOTConfig> findByCompanyIdAndConfigDateBetween(Long companyId, LocalDate from, LocalDate to);
}
