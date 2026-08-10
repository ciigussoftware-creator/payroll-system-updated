package com.payroll.web.repository;

import com.payroll.core.entity.CloudWorkingDaysConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CloudWorkingDaysConfigRepository extends JpaRepository<CloudWorkingDaysConfig, Long> {
    Optional<CloudWorkingDaysConfig> findByCompanyIdAndPeriodMonth(Long companyId, String periodMonth);
}
