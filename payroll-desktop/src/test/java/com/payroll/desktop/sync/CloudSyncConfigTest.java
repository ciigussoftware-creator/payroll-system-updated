package com.payroll.desktop.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The apiKey()-throws tests are skipped rather than failed when SYNC_API_KEY happens to be
 * set in the running environment (e.g. a developer's shell), since that's a legitimate state
 * this test has no business overriding — see RealCloudSyncClientTest for the no-env-dependent
 * proof that resolution never fires eagerly at construction time regardless of this variable.
 */
class CloudSyncConfigTest {

    @Test
    @DisabledIfEnvironmentVariable(named = "SYNC_API_KEY", matches = ".+")
    void apiKeyThrowsClearErrorWhenUnset() {
        assertThatThrownBy(CloudSyncConfig::apiKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SYNC_API_KEY");
    }

    @Test
    void baseUrlAlwaysResolvesWithoutThrowing() {
        assertThat(CloudSyncConfig.BASE_URL).isNotBlank();
    }
}
