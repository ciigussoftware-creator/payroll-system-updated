package com.payroll.desktop.auth;

import com.payroll.core.entity.UserAccount;
import com.payroll.core.entity.UserRole;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.ui.auth.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FirstRunDetectionIT {

    @Test
    void freshDatabaseHasZeroAccounts(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            UserAccountRepository repo = new UserAccountRepository(db.getSessionFactory());
            assertThat(repo.countAll()).isEqualTo(0);
        }
    }

    @Test
    void afterCreatingBothAccountsCountIsTwo(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            UserAccountRepository repo = new UserAccountRepository(db.getSessionFactory());
            PasswordHasher hasher = new PasswordHasher();

            UserAccount admin = new UserAccount();
            admin.setUsername("admin");
            admin.setPasswordHash(hasher.hash("adminpass"));
            admin.setRole(UserRole.ADMIN);
            repo.save(admin);

            UserAccount superAdmin = new UserAccount();
            superAdmin.setUsername("superadmin");
            superAdmin.setPasswordHash(hasher.hash("superpass"));
            superAdmin.setRole(UserRole.SUPER_ADMIN);
            repo.save(superAdmin);

            assertThat(repo.countAll()).isEqualTo(2);
        }
    }

    @Test
    void userAccountRoundTrip(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            UserAccountRepository repo = new UserAccountRepository(db.getSessionFactory());
            PasswordHasher hasher = new PasswordHasher();

            UserAccount account = new UserAccount();
            account.setUsername("owner");
            account.setPasswordHash(hasher.hash("ownerpass"));
            account.setRole(UserRole.SUPER_ADMIN);
            repo.save(account);

            var found = repo.findByUsername("owner");
            assertThat(found).isPresent();
            assertThat(found.get().getRole()).isEqualTo(UserRole.SUPER_ADMIN);
            assertThat(found.get().isActive()).isTrue();
            assertThat(found.get().getCreatedAt()).isNotNull();
            assertThat(hasher.verify("ownerpass", found.get().getPasswordHash())).isTrue();
        }
    }

    @Test
    void findAllReturnsAllSavedAccounts(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            UserAccountRepository repo = new UserAccountRepository(db.getSessionFactory());
            PasswordHasher hasher = new PasswordHasher();

            UserAccount a = new UserAccount();
            a.setUsername("a");
            a.setPasswordHash(hasher.hash("pass"));
            a.setRole(UserRole.ADMIN);
            repo.save(a);

            UserAccount b = new UserAccount();
            b.setUsername("b");
            b.setPasswordHash(hasher.hash("pass"));
            b.setRole(UserRole.SUPER_ADMIN);
            repo.save(b);

            assertThat(repo.findAll()).hasSize(2)
                    .extracting(UserAccount::getUsername)
                    .containsExactlyInAnyOrder("a", "b");
        }
    }
}
