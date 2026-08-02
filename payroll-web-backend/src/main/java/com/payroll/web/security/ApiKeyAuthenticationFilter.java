package com.payroll.web.security;

import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.SyncClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final SyncClientRepository syncClientRepository;
    private final ApiKeyHasher apiKeyHasher;

    public ApiKeyAuthenticationFilter(SyncClientRepository syncClientRepository, ApiKeyHasher apiKeyHasher) {
        this.syncClientRepository = syncClientRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            String hash = apiKeyHasher.hash(apiKey);
            syncClientRepository.findByApiKeyHash(hash)
                    .filter(SyncClient::isActive)
                    .ifPresent(client -> {
                        var authentication = new SyncClientAuthentication(client.getId(), client.getCompanyId());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }
}
