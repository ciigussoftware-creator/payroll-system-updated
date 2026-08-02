package com.payroll.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.core.entity.WebAdminAccount;
import com.payroll.core.security.PasswordHasher;
import com.payroll.web.repository.WebAdminAccountRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Separate context from AuthControllerIT, with the app's Clock bean replaced
 * by a mutable one so the rate-limit window can be fast-forwarded
 * deterministically instead of relying on a real sleep — Argon2 verification
 * on every failed attempt is deliberately slow and would otherwise race
 * against a wall-clock window.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
@TestPropertySource(properties = "security.login-rate-limit.window-ms=900000")
class LoginRateLimitWindowIT {

    private static final String KNOWN_USERNAME = "testadmin";
    private static final String KNOWN_PASSWORD = "correct-horse-battery-staple";

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        public Clock clock() {
            return new MutableClock(Instant.now(), ZoneOffset.UTC);
        }
    }

    private static class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebAdminAccountRepository repository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @BeforeEach
    void seedAccount() {
        repository.deleteAll();
        WebAdminAccount account = new WebAdminAccount();
        account.setUsername(KNOWN_USERNAME);
        account.setPasswordHash(passwordHasher.hash(KNOWN_PASSWORD));
        account.setActive(true);
        repository.save(account);
    }

    @Test
    void attemptsAreAllowedAgainAfterWindowExpires() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, "wrong-password"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, KNOWN_PASSWORD))))
                .andExpect(status().isUnauthorized());

        ((MutableClock) clock).advance(Duration.ofMillis(900_001));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, KNOWN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
