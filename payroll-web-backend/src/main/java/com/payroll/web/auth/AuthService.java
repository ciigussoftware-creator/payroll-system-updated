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
    private final LoginRateLimiter rateLimiter;

    public AuthService(WebAdminAccountRepository repository, PasswordHasher passwordHasher, JwtService jwtService,
                        LoginRateLimiter rateLimiter) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    public String login(String username, String password) {
        // Blocked usernames get rejected without touching the password hash check,
        // using the same generic message as any other failure — a distinct
        // "locked out" response would itself confirm the username exists.
        if (rateLimiter.isBlocked(username)) {
            throw new BadCredentialsException("Invalid username or password");
        }

        WebAdminAccount account = repository.findByUsername(username).orElse(null);
        String hashToVerify = (account != null) ? account.getPasswordHash() : DUMMY_HASH;
        boolean passwordOk = passwordHasher.verify(password, hashToVerify);

        if (account == null || !account.isActive() || !passwordOk) {
            rateLimiter.recordFailure(username);
            throw new BadCredentialsException("Invalid username or password");
        }
        rateLimiter.recordSuccess(username);
        return jwtService.generateToken(account.getUsername());
    }
}
