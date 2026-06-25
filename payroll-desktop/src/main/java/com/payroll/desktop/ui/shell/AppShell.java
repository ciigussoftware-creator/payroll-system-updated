package com.payroll.desktop.ui.shell;

import com.payroll.desktop.PayrollApp;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.ui.auth.AuthService;
import com.payroll.desktop.ui.auth.FirstRunSetup;
import com.payroll.desktop.ui.auth.LoginScreen;
import com.payroll.desktop.ui.auth.PasswordHasher;
import com.payroll.desktop.ui.auth.UserSession;
import com.payroll.desktop.ui.superadmin.SuperAdminShell;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class AppShell {

    private final Stage stage;
    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final AuthService authService;

    public AppShell(Stage stage,
                    UserAccountRepository userAccountRepository,
                    PasswordHasher passwordHasher,
                    AuthService authService) {
        this.stage = stage;
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.authService = authService;
    }

    public void start() {
        stage.setTitle("Payroll System");
        if (userAccountRepository.countAll() == 0) {
            showFirstRunSetup();
        } else {
            showLogin();
        }
        stage.show();
    }

    private void showFirstRunSetup() {
        var setup = new FirstRunSetup(userAccountRepository, passwordHasher, this::showLogin);
        stage.setScene(styledScene(setup, 400, 580));
    }

    private void showLogin() {
        var login = new LoginScreen(authService, this::showShell);
        stage.setScene(styledScene(login, 420, 320));
    }

    private void showShell(UserSession session) {
        Runnable onLogout = this::showLogin;
        BorderPane shell = switch (session.getRole()) {
            case ADMIN      -> new AdminShell(session, onLogout);
            case SUPER_ADMIN -> new SuperAdminShell(session, onLogout);
        };
        stage.setScene(styledScene(shell, 1280, 800));
    }

    /** Creates a Scene and attaches the app-wide stylesheet. */
    private static Scene styledScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(PayrollApp.APP_CSS);
        return scene;
    }
}

