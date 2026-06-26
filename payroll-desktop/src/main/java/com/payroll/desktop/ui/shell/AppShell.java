package com.payroll.desktop.ui.shell;

import com.payroll.desktop.PayrollApp;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.UserAccountRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import com.payroll.desktop.statutory.StatutoryCalculationService;
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
    private final EmployeeRepository employeeRepository;
    private final WorkingDaysConfigRepository workingDaysRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final StatutoryCalculationService statutoryService;
    private final StatutoryOverrideRepository overrideRepository;

    public AppShell(Stage stage,
                    UserAccountRepository userAccountRepository,
                    PasswordHasher passwordHasher,
                    AuthService authService,
                    EmployeeRepository employeeRepository,
                    WorkingDaysConfigRepository workingDaysRepository,
                    AttendanceRecordRepository attendanceRepository,
                    StatutoryCalculationService statutoryService,
                    StatutoryOverrideRepository overrideRepository) {
        this.stage = stage;
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.authService = authService;
        this.employeeRepository = employeeRepository;
        this.workingDaysRepository = workingDaysRepository;
        this.attendanceRepository = attendanceRepository;
        this.statutoryService = statutoryService;
        this.overrideRepository = overrideRepository;
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
            case ADMIN       -> new AdminShell(session, onLogout, employeeRepository,
                                               workingDaysRepository, attendanceRepository,
                                               statutoryService, overrideRepository);
            case SUPER_ADMIN -> new SuperAdminShell(session, onLogout, employeeRepository,
                                                    workingDaysRepository, attendanceRepository,
                                                    statutoryService, overrideRepository);
        };
        stage.setScene(styledScene(shell, 1280, 800));
    }

    private static Scene styledScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(PayrollApp.APP_CSS);
        return scene;
    }
}
