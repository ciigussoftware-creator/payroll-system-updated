package com.payroll.desktop.auth;

import com.payroll.desktop.ui.auth.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashThenVerifyReturnsTrue() {
        String hash = hasher.hash("secret123");
        assertThat(hasher.verify("secret123", hash)).isTrue();
    }

    @Test
    void wrongPasswordReturnsFalse() {
        String hash = hasher.hash("correcthorse");
        assertThat(hasher.verify("wrongpassword", hash)).isFalse();
    }

    @Test
    void twoHashesOfSamePasswordDiffer() {
        String hash1 = hasher.hash("same");
        String hash2 = hasher.hash("same");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
