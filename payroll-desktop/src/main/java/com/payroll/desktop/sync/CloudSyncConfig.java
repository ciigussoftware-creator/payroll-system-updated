package com.payroll.desktop.sync;

/**
 * Cloud sync endpoint + credential for this factory's desktop install.
 *
 * TODO (secret management): both values are hardcoded constants for now, matching
 * the MVP pattern used for the local DB encryption key (see DbEncryptionKey). Before
 * any real multi-factory rollout, move API_KEY out of source (env var, OS keychain,
 * or a per-install config file) so each factory's desktop build doesn't ship every
 * other factory's key material.
 */
final class CloudSyncConfig {

    static final String BASE_URL = "http://localhost:8080";

    // Replace with the key printed by SyncClientBootstrapRunner on the backend for this factory.
    static final String API_KEY = "P0Psnr1KZvBUmwhyW9Csi35uXI0uP5FuxmtvY4T6UZw";

    private CloudSyncConfig() {}
}
