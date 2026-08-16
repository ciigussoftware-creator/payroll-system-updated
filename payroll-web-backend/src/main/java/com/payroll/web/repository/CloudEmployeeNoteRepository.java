package com.payroll.web.repository;

import com.payroll.core.entity.CloudEmployeeNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CloudEmployeeNoteRepository extends JpaRepository<CloudEmployeeNote, Long> {
    List<CloudEmployeeNote> findByCompanyIdAndEmployeeCodeOrderByCreatedAtDesc(Long companyId, String employeeCode);
}
