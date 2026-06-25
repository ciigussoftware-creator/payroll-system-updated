package com.payroll.desktop.auth;

import com.payroll.core.entity.UserAccount;
import com.payroll.core.entity.UserRole;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.ui.auth.AuthService;
import com.payroll.desktop.ui.auth.PasswordHasher;
import com.payroll.desktop.ui.auth.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    @TempDir Path tempDir;

    private DatabaseManager db;
    private AuthService authService;
    private PasswordHasher hasher;

    @BeforeEach
    void setUp() throws IOException {
        db = new DatabaseManager(tempDir);
        hasher = new PasswordHasher();
        var repo = new UserAccountRepository(db.getSessionFactory());
        authService = new AuthService(repo, hasher);

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
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void correctCredentialsReturnSessionWithCorrectRole() {
        Optional<UserSession> session = authService.authenticate("admin", "adminpass");
        assertThat(session).isPresent();
        assertThat(session.get().getUsername()).isEqualTo("admin");
        assertThat(session.get().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void superAdminCredentialsReturnSuperAdminRole() {
        Optional<UserSession> session = authService.authenticate("superadmin", "superpass");
        assertThat(session).isPresent();
        assertThat(session.get().getRole()).isEqualTo(UserRole.SUPER_ADMIN);
    }

    @Test
    void wrongPasswordReturnsEmpty() {
        Optional<UserSession> session = authService.authenticate("admin", "wrongpassword");
        assertThat(session).isEmpty();
    }

    @Test
    void unknownUsernameReturnsEmpty() {
        Optional<UserSession> session = authService.authenticate("nobody", "adminpass");
        assertThat(session).isEmpty();
    }
}
