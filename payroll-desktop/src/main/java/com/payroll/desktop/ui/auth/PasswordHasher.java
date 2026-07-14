package com.payroll.desktop.ui.auth;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

import java.util.Arrays;

public class PasswordHasher {

    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Types.ARGON2id);
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536; // 64 MB — OWASP recommended minimum
    private static final int PARALLELISM = 1;

    public String hash(String plaintext) {
        char[] chars = plaintext.toCharArray();
        try {
            return ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    public boolean verify(String plaintext, String hash) {
        char[] chars = plaintext.toCharArray();
        try {
            return ARGON2.verify(hash, chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
