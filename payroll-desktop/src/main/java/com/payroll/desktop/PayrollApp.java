package com.payroll.desktop;

import atlantafx.base.theme.PrimerLight;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeNoteRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import com.payroll.desktop.statutory.StatutoryCalculationService;
import com.payroll.desktop.sync.RealCloudSyncClient;
import com.payroll.desktop.sync.SyncScheduler;
import com.payroll.desktop.sync.SyncService;
import com.payroll.desktop.ui.auth.AuthService;
import com.payroll.desktop.ui.auth.PasswordHasher;
import com.payroll.desktop.ui.shell.AppShell;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayrollApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(PayrollApp.class);

    private DatabaseManager databaseManager;
    private SyncScheduler syncScheduler;

    public static final String APP_CSS =
            PayrollApp.class.getResource("/com/payroll/desktop/css/app.css").toExternalForm();

    @Override
    public void start(Stage primaryStage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        databaseManager = new DatabaseManager();
        var sessionFactory = databaseManager.getSessionFactory();

        var userAccountRepo  = new UserAccountRepository(sessionFactory);
        var employeeRepo     = new EmployeeRepository(sessionFactory);
        var workingDaysRepo  = new WorkingDaysConfigRepository(sessionFactory);
        var attendanceRepo   = new AttendanceRecordRepository(sessionFactory);
        var dayLevelOTRepo   = new DayLevelOTConfigRepository(sessionFactory);
        var overrideRepo     = new StatutoryOverrideRepository(sessionFactory);
        var auditLogRepo     = new AuditLogRepository(sessionFactory);
        var otAuthRepo       = new OtEmployeeAuthorizationRepository(sessionFactory);
        var employeeNoteRepo = new EmployeeNoteRepository(sessionFactory);
        var hasher           = new PasswordHasher();
        var authService      = new AuthService(userAccountRepo, hasher);
        var statutoryService = new StatutoryCalculationService(
                attendanceRepo, employeeRepo, workingDaysRepo, dayLevelOTRepo, overrideRepo);

        var syncService = new SyncService(employeeRepo, workingDaysRepo, dayLevelOTRepo, otAuthRepo, attendanceRepo, new RealCloudSyncClient());
        syncScheduler = new SyncScheduler(syncService);

        new AppShell(primaryStage, userAccountRepo, hasher, authService,
                     employeeRepo, workingDaysRepo, attendanceRepo,
                     statutoryService, overrideRepo,
                     dayLevelOTRepo, auditLogRepo, otAuthRepo, employeeNoteRepo,
                     syncScheduler).start();

        try {
            syncScheduler.start();
        } catch (Exception e) {
            LOG.warn("Cloud sync scheduler failed to start; sync will not run automatically", e);
        }
    }

    @Override
    public void stop() {
        if (syncScheduler != null) {
            try {
                syncScheduler.shutdown();
            } catch (Exception e) {
                LOG.warn("Cloud sync scheduler failed to shut down cleanly", e);
            }
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
