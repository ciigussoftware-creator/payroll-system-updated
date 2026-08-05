package com.payroll.web.bootstrap;

import com.payroll.core.entity.Company;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.SyncClientRepository;
import com.payroll.web.security.ApiKeyHasher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * DEV/BOOTSTRAP ONLY: seeds two demo companies and one active SyncClient (API
 * key) per company the first time the app starts against an empty company
 * table. Each generated key is printed once, in the clear, labeled by company
 * — this is the only time the plaintext key is ever available; only its hash
 * is persisted.
 */
@Component
public class SyncClientBootstrapRunner implements CommandLineRunner {

    private static final List<String[]> DEV_COMPANIES = List.of(
            new String[] {"Wood Lanka", "WL"},
            new String[] {"DCH Plywood", "DCH"}
    );

    private final CompanyRepository companyRepository;
    private final SyncClientRepository syncClientRepository;
    private final ApiKeyHasher apiKeyHasher;

    public SyncClientBootstrapRunner(CompanyRepository companyRepository,
                                      SyncClientRepository syncClientRepository,
                                      ApiKeyHasher apiKeyHasher) {
        this.companyRepository = companyRepository;
        this.syncClientRepository = syncClientRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    @Override
    public void run(String... args) {
        if (companyRepository.count() > 0) {
            return;
        }

        // Printed directly to the console rather than logged, so the key is never
        // captured by a structured log aggregator (ELK, CloudWatch, etc.) that may
        // be attached to this app.
        System.out.println("=== DEV BOOTSTRAP: created sync API keys (shown only once) ===");
        for (String[] entry : DEV_COMPANIES) {
            String name = entry[0];
            String code = entry[1];

            Company company = new Company();
            company.setName(name);
            company.setCode(code);
            company = companyRepository.save(company);

            String apiKey = generateApiKey();

            SyncClient syncClient = new SyncClient();
            syncClient.setCompanyId(company.getId());
            syncClient.setApiKeyHash(apiKeyHasher.hash(apiKey));
            syncClient.setActive(true);
            syncClientRepository.save(syncClient);

            System.out.println("Company: " + name + " (" + code + ")  API key: " + apiKey);
        }
        System.out.println("================================================================");
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
