package com.payroll.web.security;

import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.SyncClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final int PREFIX_LENGTH = 8;

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
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("API key rejected: no {} header provided for {} {}",
                    API_KEY_HEADER, request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String hash = apiKeyHasher.hash(apiKey);
        Optional<SyncClient> client = syncClientRepository.findByApiKeyHash(hash);

        if (client.isEmpty()) {
            log.warn("API key rejected: no matching sync client for key prefix '{}...'", keyPrefix(apiKey));
            filterChain.doFilter(request, response);
            return;
        }

        if (!client.get().isActive()) {
            log.warn("API key rejected: sync client id={} (key prefix '{}...') is inactive",
                    client.get().getId(), keyPrefix(apiKey));
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = new SyncClientAuthentication(client.get().getId(), client.get().getCompanyId());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private static String keyPrefix(String apiKey) {
        return apiKey.length() <= PREFIX_LENGTH ? apiKey : apiKey.substring(0, PREFIX_LENGTH);
    }
}
