package com.payroll.web.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, single-instance failed-login tracker. No external dependency
 * (Redis, etc.) needed at this scale — state resets on app restart, which is
 * acceptable for a brute-force speed bump.
 */
@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, Attempts> attemptsByUsername = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimiter(Clock clock,
                             @Value("${security.login-rate-limit.max-attempts:5}") int maxAttempts,
                             @Value("${security.login-rate-limit.window-ms:900000}") long windowMs) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMillis(windowMs);
    }

    public boolean isBlocked(String username) {
        Attempts attempts = attemptsByUsername.get(username);
        if (attempts == null) {
            return false;
        }
        if (isExpired(attempts)) {
            attemptsByUsername.remove(username);
            return false;
        }
        return attempts.count >= maxAttempts;
    }

    public void recordFailure(String username) {
        attemptsByUsername.compute(username, (key, existing) -> {
            Instant now = clock.instant();
            if (existing == null || isExpired(existing)) {
                return new Attempts(now, 1);
            }
            return new Attempts(existing.windowStart, existing.count + 1);
        });
    }

    public void recordSuccess(String username) {
        attemptsByUsername.remove(username);
    }

    private boolean isExpired(Attempts attempts) {
        return Duration.between(attempts.windowStart, clock.instant()).compareTo(window) > 0;
    }

    private record Attempts(Instant windowStart, int count) {
    }
}
