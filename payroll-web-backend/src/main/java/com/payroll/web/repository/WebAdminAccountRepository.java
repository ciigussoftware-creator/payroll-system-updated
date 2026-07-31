package com.payroll.web.repository;

import com.payroll.core.entity.WebAdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebAdminAccountRepository extends JpaRepository<WebAdminAccount, Long> {
    Optional<WebAdminAccount> findByUsername(String username);
}
