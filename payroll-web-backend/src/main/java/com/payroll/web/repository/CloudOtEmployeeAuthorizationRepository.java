package com.payroll.web.repository;

import com.payroll.core.entity.CloudOtEmployeeAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CloudOtEmployeeAuthorizationRepository extends JpaRepository<CloudOtEmployeeAuthorization, Long> {
    Optional<CloudOtEmployeeAuthorization> findByCompanyIdAndEmployeeCodeAndAuthDate(
            Long companyId, String employeeCode, LocalDate authDate);

    List<CloudOtEmployeeAuthorization> findByCompanyIdAndAuthDateBetween(Long companyId, LocalDate from, LocalDate to);
}
