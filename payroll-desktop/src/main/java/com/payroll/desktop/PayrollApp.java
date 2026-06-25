package com.payroll.desktop;

import atlantafx.base.theme.PrimerLight;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.ui.auth.AuthService;
import com.payroll.desktop.ui.auth.PasswordHasher;
import com.payroll.desktop.ui.shell.AppShell;
import javafx.application.Application;
import javafx.stage.Stage;

public class PayrollApp extends Application {

    private DatabaseManager databaseManager;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        databaseManager = new DatabaseManager();
        var sessionFactory = databaseManager.getSessionFactory();
        var repo = new UserAccountRepository(sessionFactory);
        var hasher = new PasswordHasher();
        var authService = new AuthService(repo, hasher);

        new AppShell(primaryStage, repo, hasher, authService).start();
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
