package com.payroll.desktop;

import atlantafx.base.theme.PrimerLight;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import com.payroll.desktop.statutory.StatutoryCalculationService;
import com.payroll.desktop.ui.auth.AuthService;
import com.payroll.desktop.ui.auth.PasswordHasher;
import com.payroll.desktop.ui.shell.AppShell;
import javafx.application.Application;
import javafx.stage.Stage;

public class PayrollApp extends Application {

    private DatabaseManager databaseManager;

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
        var hasher           = new PasswordHasher();
        var authService      = new AuthService(userAccountRepo, hasher);
        var statutoryService = new StatutoryCalculationService(
                attendanceRepo, employeeRepo, workingDaysRepo, dayLevelOTRepo, overrideRepo);

        new AppShell(primaryStage, userAccountRepo, hasher, authService,
                     employeeRepo, workingDaysRepo, attendanceRepo,
                     statutoryService, overrideRepo).start();
    }

    @Override
    public void stop() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
