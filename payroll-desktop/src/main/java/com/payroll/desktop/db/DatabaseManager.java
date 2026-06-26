package com.payroll.desktop.db;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.DayLevelOTConfig;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.PayrollCalculation;
import com.payroll.core.entity.StatutoryOverride;
import com.payroll.core.entity.UserAccount;
import com.payroll.core.entity.WorkingDaysConfig;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the lifecycle of the local encrypted H2 database and the Hibernate SessionFactory.
 * Uses H2's built-in AES file encryption (CIPHER=AES in the JDBC URL).
 * <p>
 * The JDBC password is a two-part string: "&lt;filePassword&gt; &lt;userPassword&gt;".
 * H2 uses the first token to decrypt/encrypt the file and the second token for user auth.
 */
public class DatabaseManager implements AutoCloseable {

    private static final String DB_NAME = "payroll";
    private static final String DB_USER = "sa";

    private final SessionFactory sessionFactory;

    /** Production constructor — uses the default app-data directory. */
    public DatabaseManager() throws IOException {
        this(defaultDbDir());
    }

    /** Constructor with a custom database directory; uses the built-in key. */
    public DatabaseManager(Path dbDir) throws IOException {
        this(dbDir, DbEncryptionKey.FILE_PASSWORD);
    }

    /**
     * Low-level constructor. Package-private so tests can inject an alternate file
     * password (e.g., to prove that a wrong key cannot open the encrypted file).
     */
    DatabaseManager(Path dbDir, String filePassword) throws IOException {
        Files.createDirectories(dbDir);
        String jdbcUrl = buildJdbcUrl(dbDir);
        String combinedPassword = filePassword + " " + DB_USER;
        this.sessionFactory = buildSessionFactory(jdbcUrl, combinedPassword);
    }

    private static Path defaultDbDir() {
        return Path.of(System.getProperty("user.home"), ".payroll");
    }

    private static String buildJdbcUrl(Path dbDir) {
        // H2 appends .mv.db automatically; normalise to forward slashes for cross-platform safety
        String filePath = dbDir.resolve(DB_NAME).toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + filePath + ";CIPHER=AES";
    }

    private static SessionFactory buildSessionFactory(String jdbcUrl, String password) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .applySetting("jakarta.persistence.jdbc.url", jdbcUrl)
                .applySetting("jakarta.persistence.jdbc.user", DB_USER)
                .applySetting("jakarta.persistence.jdbc.password", password)
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.show_sql", "false")
                .build();

        try {
            return new MetadataSources(registry)
                    .addAnnotatedClass(Employee.class)
                    .addAnnotatedClass(AttendanceRecord.class)
                    .addAnnotatedClass(DayLevelOTConfig.class)
                    .addAnnotatedClass(PayrollCalculation.class)
                    .addAnnotatedClass(StatutoryOverride.class)
                    .addAnnotatedClass(UserAccount.class)
                    .addAnnotatedClass(WorkingDaysConfig.class)
                    .buildMetadata()
                    .buildSessionFactory();
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    @Override
    public void close() {
        sessionFactory.close();
    }
}
