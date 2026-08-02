package com.payroll.web.ping;

import com.payroll.web.security.JwtService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class PingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void pingWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pingWithValidTokenReturns200() throws Exception {
        String token = jwtService.generateToken("testadmin");

        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"))
                .andExpect(jsonPath("$.user").value("testadmin"));
    }

    @Test
    void pingWithExpiredTokenReturns401() throws Exception {
        String token = jwtService.generateToken("testadmin", Instant.now().minusSeconds(10));

        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pingWithTamperedTokenReturns401() throws Exception {
        String token = jwtService.generateToken("testadmin");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
