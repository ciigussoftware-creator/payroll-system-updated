package com.payroll.web.bootstrap;

import com.payroll.core.entity.Company;
import com.payroll.core.entity.SyncClient;
import com.payroll.web.repository.CompanyRepository;
import com.payroll.web.repository.SyncClientRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the real app against an empty embedded database with COMPANY_CODE /
 * COMPANY_NAME set. The test config also sets the dev-companies flag, so this
 * covers "explicit company wins over the flag" end to end.
 */
@SpringBootTest(properties = {
        "bootstrap.company.code=ACME",
        "bootstrap.company.name=Acme Timber"
})
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class SyncClientBootstrapIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private SyncClientRepository syncClientRepository;

    @Test
    void startupWithCompanyVarsPersistsOneCompanyAndOneActiveClient() {
        List<Company> companies = companyRepository.findAll();
        assertEquals(1, companies.size());
        assertEquals("ACME", companies.get(0).getCode());
        assertEquals("Acme Timber", companies.get(0).getName());

        List<SyncClient> clients = syncClientRepository.findAll();
        assertEquals(1, clients.size());
        assertEquals(companies.get(0).getId(), clients.get(0).getCompanyId());
        assertTrue(clients.get(0).isActive());
    }
}
