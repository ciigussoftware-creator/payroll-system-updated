package com.payroll.web.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Authentication principal for the API-key filter chain. Carries the resolved
 * companyId so the sync controller can scope every write to the caller's own factory.
 */
public class SyncClientAuthentication extends AbstractAuthenticationToken {

    private final Long syncClientId;
    private final Long companyId;

    public SyncClientAuthentication(Long syncClientId, Long companyId) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.syncClientId = syncClientId;
        this.companyId = companyId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return companyId;
    }

    public Long getSyncClientId() {
        return syncClientId;
    }

    public Long getCompanyId() {
        return companyId;
    }
}
