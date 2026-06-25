package com.payroll.desktop.ui.auth;

import com.payroll.desktop.repository.UserAccountRepository;

import java.util.Optional;

public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UserAccountRepository userAccountRepository, PasswordHasher passwordHasher) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Returns a session on success; empty on any failure.
     * Callers must show "invalid username or password" — never reveal which field was wrong.
     */
    public Optional<UserSession> authenticate(String username, String password) {
        return userAccountRepository.findByUsername(username)
                .filter(ua -> ua.isActive())
                .filter(ua -> passwordHasher.verify(password, ua.getPasswordHash()))
                .map(ua -> new UserSession(ua.getUsername(), ua.getRole()));
    }
}
