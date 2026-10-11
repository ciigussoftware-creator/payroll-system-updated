package com.payroll.web.bootstrap;

import com.payroll.core.entity.Company;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.SyncClientRepository;
import com.payroll.web.security.ApiKeyHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Seeds the company table and one active SyncClient (API key) per company the
 * first time the app starts against an empty company table.
 *
 * <ul>
 *   <li>{@code COMPANY_CODE} and {@code COMPANY_NAME} both set: creates only
 *       that company. This wins even when {@code BOOTSTRAP_DEV_COMPANIES=true}.</li>
 *   <li>Exactly one of them set: startup fails naming the missing variable,
 *       regardless of the dev flag — a half-set pair is almost certainly a
 *       deployment typo.</li>
 *   <li>Neither set: seeds the two demo companies if
 *       {@code BOOTSTRAP_DEV_COMPANIES=true}, otherwise startup fails.</li>
 * </ul>
 *
 * Each generated key is printed once, in the clear, labeled by company — this is
 * the only time the plaintext key is ever available; only its hash is persisted.
 *
 * <p>Runs before {@link AdminBootstrapRunner} so that a misconfigured first boot
 * fails before the admin's one-time password is printed and then lost with the
 * crashed run's output.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncClientBootstrapRunner implements CommandLineRunner {

    static final String CODE_VAR = "COMPANY_CODE";
    static final String NAME_VAR = "COMPANY_NAME";
    static final String DEV_FLAG_VAR = "BOOTSTRAP_DEV_COMPANIES";

    /** Matches the {@code companies.code} column length. */
    private static final int MAX_CODE_LENGTH = 20;

    private static final List<String[]> DEV_COMPANIES = List.of(
            new String[] {"Wood Lanka", "WL"},
            new String[] {"DCH Plywood", "DCH"}
    );

    private final CompanyRepository companyRepository;
    private final SyncClientRepository syncClientRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final String companyCode;
    private final String companyName;
    private final boolean devCompanies;

    public SyncClientBootstrapRunner(CompanyRepository companyRepository,
                                      SyncClientRepository syncClientRepository,
                                      ApiKeyHasher apiKeyHasher,
                                      @Value("${bootstrap.company.code:}") String companyCode,
                                      @Value("${bootstrap.company.name:}") String companyName,
                                      @Value("${bootstrap.dev-companies:false}") boolean devCompanies) {
        this.companyRepository = companyRepository;
        this.syncClientRepository = syncClientRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.companyCode = companyCode == null ? "" : companyCode.trim();
        this.companyName = companyName == null ? "" : companyName.trim();
        this.devCompanies = devCompanies;
    }

    @Override
    public void run(String... args) {
        if (companyRepository.count() > 0) {
            return;
        }

        List<String[]> companies = resolveCompanies();

        // Printed directly to the console rather than logged, so the key is never
        // captured by a structured log aggregator (ELK, CloudWatch, etc.) that may
        // be attached to this app.
        System.out.println("=== BOOTSTRAP: created sync API keys (shown only once) ===");
        for (String[] entry : companies) {
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

    private List<String[]> resolveCompanies() {
        boolean hasCode = !companyCode.isEmpty();
        boolean hasName = !companyName.isEmpty();

        if (hasCode && hasName) {
            if (companyCode.length() > MAX_CODE_LENGTH) {
                throw new IllegalStateException(CODE_VAR + " must be at most " + MAX_CODE_LENGTH
                        + " characters, got " + companyCode.length() + ".");
            }
            return List.<String[]>of(new String[] {companyName, companyCode});
        }

        if (hasCode || hasName) {
            String missing = hasCode ? NAME_VAR : CODE_VAR;
            String present = hasCode ? CODE_VAR : NAME_VAR;
            throw new IllegalStateException("Cannot bootstrap company: " + missing + " is not set but "
                    + present + " is. Set both " + CODE_VAR + " and " + NAME_VAR
                    + " (" + DEV_FLAG_VAR + " does not apply when only one is set).");
        }

        if (devCompanies) {
            return DEV_COMPANIES;
        }

        throw new IllegalStateException("Cannot bootstrap company: " + CODE_VAR + " and " + NAME_VAR
                + " are not set. Set both to create this deployment's company, or set "
                + DEV_FLAG_VAR + "=true to seed the demo companies (dev only).");
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
