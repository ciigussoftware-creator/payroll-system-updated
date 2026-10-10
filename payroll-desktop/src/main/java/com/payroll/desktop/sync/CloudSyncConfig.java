package com.payroll.desktop.sync;

/**
 * Cloud sync endpoint + credential for this factory's desktop install.
 *
 * BASE_URL defaults to localhost for local dev but can be overridden via SYNC_BASE_URL —
 * resolved eagerly since it never fails. API_KEY has no fallback on purpose: a key baked
 * into source control would let every factory's desktop build sync with every other
 * factory's credentials. Local-only work (no sync attempted) is a supported offline mode,
 * so API_KEY is resolved lazily via {@link #apiKey()} — called only when a sync is actually
 * attempted — rather than eagerly, which would crash the app at launch for anyone not
 * syncing that session.
 */
final class CloudSyncConfig {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    static final String BASE_URL = resolveBaseUrl();

    private static String resolveBaseUrl() {
        String value = System.getenv("SYNC_BASE_URL");
        return (value == null || value.isBlank()) ? DEFAULT_BASE_URL : value;
    }

    // Set to the key printed by SyncClientBootstrapRunner on the backend for this factory.
    static String apiKey() {
        String value = System.getenv("SYNC_API_KEY");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "SYNC_API_KEY environment variable is not set. Set it to the API key issued by "
                    + "SyncClientBootstrapRunner on the backend for this factory before attempting a cloud sync.");
        }
        return value;
    }

    private CloudSyncConfig() {}
}
