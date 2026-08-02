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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class AuthControllerIT {

    private static final String KNOWN_USERNAME = "testadmin";
    private static final String KNOWN_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebAdminAccountRepository repository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void seedAccount() {
        repository.deleteAll();
        WebAdminAccount account = new WebAdminAccount();
        account.setUsername(KNOWN_USERNAME);
        account.setPasswordHash(passwordHasher.hash(KNOWN_PASSWORD));
        account.setActive(true);
        repository.save(account);

        // Rate limiter state is a singleton bean shared across tests in this
        // class — clear it so one test's failures don't bleed into the next.
        rateLimiter.recordSuccess(KNOWN_USERNAME);
    }

    private void loginExpectUnauthorized(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, KNOWN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturnsGeneric401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginWithUnknownUsernameReturnsSameGeneric401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("no-such-user", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void sixthAttemptIsRejectedEvenWithCorrectPasswordAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginExpectUnauthorized(KNOWN_USERNAME, "wrong-password");
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, KNOWN_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void successfulLoginResetsFailedAttemptCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginExpectUnauthorized(KNOWN_USERNAME, "wrong-password");
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, KNOWN_PASSWORD))))
                .andExpect(status().isOk());

        // Counter was reset by the success above, so 4 more failures shouldn't
        // trip the 5-attempt lockout, and a correct login right after succeeds.
        for (int i = 0; i < 4; i++) {
            loginExpectUnauthorized(KNOWN_USERNAME, "wrong-password");
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, KNOWN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithNullUsernameReturnsClean400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(null, KNOWN_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @Test
    void loginWithOversizedPasswordReturnsClean400() throws Exception {
        String oversizedPassword = "a".repeat(129);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KNOWN_USERNAME, oversizedPassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }
}
