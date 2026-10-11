package com.payroll.web.bootstrap;

import com.payroll.core.entity.Company;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.SyncClientRepository;
import com.payroll.web.security.ApiKeyHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncClientBootstrapRunnerTest {

    private static final Pattern KEY_LINE = Pattern.compile("Company: .* \\((\\S+)\\)  API key: (\\S+)");

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final SyncClientRepository syncClientRepository = mock(SyncClientRepository.class);
    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();

    private final List<Company> savedCompanies = new ArrayList<>();
    private final List<SyncClient> savedClients = new ArrayList<>();

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        when(companyRepository.count()).thenReturn(0L);
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId((long) savedCompanies.size() + 1);
            savedCompanies.add(c);
            return c;
        });
        when(syncClientRepository.save(any(SyncClient.class))).thenAnswer(inv -> {
            SyncClient s = inv.getArgument(0);
            savedClients.add(s);
            return s;
        });

        originalOut = System.out;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private SyncClientBootstrapRunner runner(String code, String name, boolean devCompanies) {
        return new SyncClientBootstrapRunner(companyRepository, syncClientRepository, apiKeyHasher,
                code, name, devCompanies);
    }

    @Test
    void bothVarsSetCreatesOnlyThatCompanyAndOneActiveClient() {
        runner("ACME", "Acme Timber", false).run();

        assertEquals(1, savedCompanies.size());
        assertEquals("ACME", savedCompanies.get(0).getCode());
        assertEquals("Acme Timber", savedCompanies.get(0).getName());

        assertEquals(1, savedClients.size());
        SyncClient client = savedClients.get(0);
        assertEquals(savedCompanies.get(0).getId(), client.getCompanyId());
        assertTrue(client.isActive());

        Matcher m = KEY_LINE.matcher(stdout.toString(StandardCharsets.UTF_8));
        assertTrue(m.find(), "key line printed to stdout");
        assertEquals("ACME", m.group(1));
        assertEquals(apiKeyHasher.hash(m.group(2)), client.getApiKeyHash(), "printed key matches stored hash");
        assertFalse(m.find(), "exactly one key printed");
    }

    @Test
    void valuesAreTrimmed() {
        runner("  ACME ", " Acme Timber  ", false).run();

        assertEquals("ACME", savedCompanies.get(0).getCode());
        assertEquals("Acme Timber", savedCompanies.get(0).getName());
    }

    @Test
    void bothVarsSetWinsOverDevFlag() {
        runner("ACME", "Acme Timber", true).run();

        assertEquals(1, savedCompanies.size());
        assertEquals("ACME", savedCompanies.get(0).getCode());
        assertEquals(1, savedClients.size());
    }

    @Test
    void missingNameFailsNamingCompanyName() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner("ACME", "", false).run());

        assertTrue(ex.getMessage().contains("COMPANY_NAME is not set"), ex.getMessage());
        assertNoWrites();
    }

    @Test
    void missingCodeFailsNamingCompanyCode() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner("", "Acme Timber", false).run());

        assertTrue(ex.getMessage().contains("COMPANY_CODE is not set"), ex.getMessage());
        assertNoWrites();
    }

    @Test
    void halfSetPairFailsEvenWithDevFlag() {
        IllegalStateException missingName = assertThrows(IllegalStateException.class,
                () -> runner("ACME", "", true).run());
        assertTrue(missingName.getMessage().contains("COMPANY_NAME is not set"), missingName.getMessage());

        IllegalStateException missingCode = assertThrows(IllegalStateException.class,
                () -> runner("", "Acme Timber", true).run());
        assertTrue(missingCode.getMessage().contains("COMPANY_CODE is not set"), missingCode.getMessage());

        assertNoWrites();
    }

    @Test
    void bothMissingWithoutDevFlagFailsNamingBoth() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner("", "", false).run());

        assertTrue(ex.getMessage().contains("COMPANY_CODE and COMPANY_NAME are not set"), ex.getMessage());
        assertTrue(ex.getMessage().contains("BOOTSTRAP_DEV_COMPANIES=true"), ex.getMessage());
        assertNoWrites();
    }

    @Test
    void blankValuesCountAsMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner("   ", null, false).run());

        assertTrue(ex.getMessage().contains("COMPANY_CODE and COMPANY_NAME are not set"), ex.getMessage());
        assertNoWrites();
    }

    @Test
    void codeLongerThanColumnFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner("X".repeat(21), "Acme Timber", false).run());

        assertTrue(ex.getMessage().contains("COMPANY_CODE must be at most 20 characters"), ex.getMessage());
        assertNoWrites();
    }

    @Test
    void devFlagWithNeitherVarSeedsTwoDevCompanies() {
        runner("", "", true).run();

        assertEquals(List.of("WL", "DCH"), savedCompanies.stream().map(Company::getCode).toList());
        assertEquals(2, savedClients.size());
        assertTrue(savedClients.stream().allMatch(SyncClient::isActive));
    }

    @Test
    void existingCompaniesSkipBootstrapEvenWhenMisconfigured() {
        when(companyRepository.count()).thenReturn(1L);

        runner("ACME", "", false).run();

        assertNoWrites();
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
    }

    private void assertNoWrites() {
        verify(companyRepository, never()).save(any());
        verify(syncClientRepository, never()).save(any());
    }
}
