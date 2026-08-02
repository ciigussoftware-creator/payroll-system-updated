package com.payroll.web.auth;

import com.payroll.core.entity.WebAdminAccount;
import com.payroll.core.security.PasswordHasher;
import com.payroll.web.repository.WebAdminAccountRepository;
import com.payroll.web.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // Precomputed so an unknown username still pays the same Argon2 verify
    // cost as a real account lookup — avoids leaking existence via timing.
    private static final String DUMMY_HASH =
            new PasswordHasher().hash("dummy-password-for-timing-safety");

    private final WebAdminAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public AuthService(WebAdminAccountRepository repository, PasswordHasher passwordHasher, JwtService jwtService) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    public String login(String username, String password) {
        WebAdminAccount account = repository.findByUsername(username).orElse(null);
        String hashToVerify = (account != null) ? account.getPasswordHash() : DUMMY_HASH;
        boolean passwordOk = passwordHasher.verify(password, hashToVerify);

        if (account == null || !account.isActive() || !passwordOk) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return jwtService.generateToken(account.getUsername());
    }
}
