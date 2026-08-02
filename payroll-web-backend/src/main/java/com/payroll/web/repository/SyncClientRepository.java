package com.payroll.web.repository;

import com.payroll.core.entity.SyncClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncClientRepository extends JpaRepository<SyncClient, Long> {
    Optional<SyncClient> findByApiKeyHash(String apiKeyHash);
}
