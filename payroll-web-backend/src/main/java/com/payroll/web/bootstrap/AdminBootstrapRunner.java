package com.payroll.web.bootstrap;

import com.payroll.core.entity.WebAdminAccount;
import com.payroll.core.security.PasswordHasher;
import com.payroll.web.repository.WebAdminAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * DEV/BOOTSTRAP ONLY: creates a single default web admin account the first
 * time the app starts against an empty account table, so there is always a
 * way in. The generated password is logged once and never stored in plain
 * text — change it immediately after first login.
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String DEFAULT_USERNAME = "admin";

    private final WebAdminAccountRepository repository;
    private final PasswordHasher passwordHasher;

    public AdminBootstrapRunner(WebAdminAccountRepository repository, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        String password = generateRandomPassword();

        WebAdminAccount account = new WebAdminAccount();
        account.setUsername(DEFAULT_USERNAME);
        account.setPasswordHash(passwordHasher.hash(password));
        account.setActive(true);
        repository.save(account);

        log.warn("=== DEV BOOTSTRAP: created initial web admin account ===");
        log.warn("Username: {}", DEFAULT_USERNAME);
        log.warn("Password: {}  (shown only once — change it after first login)", password);
        log.warn("==========================================================");
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
