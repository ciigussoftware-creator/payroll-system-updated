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

        // Flip the *first* character of the signature segment, not the token's
        // last character. An HS256 signature is 32 bytes = 256 bits, which
        // base64url-encodes to 43 characters where the final character carries
        // only 4 significant bits plus 2 always-zero padding bits that decoders
        // discard. Toggling that last character between 'A' (000000) and 'B'
        // (000001) — as this test used to — only touches those padding bits,
        // so roughly 1 in 16 genuine tokens (whichever happen to end in 'A')
        // decode to byte-identical "tampered" signatures that still verify,
        // making the test intermittently pass through a token it meant to
        // corrupt. Every non-final character of a base64url segment is fully
        // significant, so flipping the first character of the signature is
        // guaranteed to change the decoded bytes on every run.
        String[] parts = token.split("\\.");
        char[] signatureChars = parts[2].toCharArray();
        signatureChars[0] = signatureChars[0] == 'A' ? 'B' : 'A';
        parts[2] = new String(signatureChars);
        String tampered = String.join(".", parts);

        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
