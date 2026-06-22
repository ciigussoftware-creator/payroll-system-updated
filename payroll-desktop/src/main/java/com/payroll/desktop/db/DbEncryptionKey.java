package com.payroll.desktop.db;

final class DbEncryptionKey {

    // TODO (V2 hardening): obfuscated constant is decompile-vulnerable.
    // Revisit with a stronger key source before production hardening.
    static final String FILE_PASSWORD = "PayrollMvpFileKey2024";

    private DbEncryptionKey() {}
}
