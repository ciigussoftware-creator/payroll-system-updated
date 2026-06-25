package com.payroll.desktop;

import atlantafx.base.theme.PrimerLight;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.UserAccountRepository;
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
        var hasher           = new PasswordHasher();
        var authService      = new AuthService(userAccountRepo, hasher);

        new AppShell(primaryStage, userAccountRepo, hasher, authService, employeeRepo).start();
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
