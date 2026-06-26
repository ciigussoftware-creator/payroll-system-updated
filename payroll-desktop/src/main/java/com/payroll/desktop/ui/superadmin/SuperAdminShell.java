package com.payroll.desktop.ui.superadmin;

import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeNoteRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import com.payroll.desktop.statutory.StatutoryCalculationService;
import com.payroll.desktop.ui.admin.CardsScreen;
import com.payroll.desktop.ui.admin.DashboardScreen;
import com.payroll.desktop.ui.admin.EmployeesScreen;
import com.payroll.desktop.ui.admin.ScanEntryScreen;
import com.payroll.desktop.ui.admin.StatutoryExportScreen;
import com.payroll.desktop.ui.admin.WorkingDaysScreen;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

public class SuperAdminShell extends BorderPane {

    private final UserSession session;
    private final Runnable onLogout;
    private final EmployeeRepository employeeRepository;
    private final WorkingDaysConfigRepository workingDaysRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final StatutoryCalculationService statutoryService;
    private final StatutoryOverrideRepository overrideRepository;
    private final AuditLogRepository auditLogRepository;
    private final OtSwitchService otSwitchService;
    private final TimestampCorrectionService timestampCorrectionService;
    private final EmployeeNoteRepository employeeNoteRepository;
    private final EmployeeNoteService employeeNoteService;

    public SuperAdminShell(UserSession session,
                           Runnable onLogout,
                           EmployeeRepository employeeRepository,
                           WorkingDaysConfigRepository workingDaysRepository,
                           AttendanceRecordRepository attendanceRepository,
                           StatutoryCalculationService statutoryService,
                           StatutoryOverrideRepository overrideRepository,
                           DayLevelOTConfigRepository dayLevelOTRepository,
                           AuditLogRepository auditLogRepository,
                           OtEmployeeAuthorizationRepository otAuthRepository,
                           EmployeeNoteRepository employeeNoteRepository) {
        this.session = session;
        this.onLogout = onLogout;
        this.employeeRepository = employeeRepository;
        this.workingDaysRepository = workingDaysRepository;
        this.attendanceRepository = attendanceRepository;
        this.statutoryService = statutoryService;
        this.overrideRepository = overrideRepository;
        this.auditLogRepository = auditLogRepository;
        this.otSwitchService = new OtSwitchService(dayLevelOTRepository, otAuthRepository, auditLogRepository);
        this.timestampCorrectionService = new TimestampCorrectionService(attendanceRepository, auditLogRepository);
        this.employeeNoteRepository = employeeNoteRepository;
        this.employeeNoteService = new EmployeeNoteService(employeeNoteRepository, auditLogRepository);
        setTop(buildTopBar());
        setLeft(buildSidebar());
        setCenter(new DashboardScreen(attendanceRepository, employeeRepository));
    }

    private HBox buildTopBar() {
        Label appName = new Label("Payroll System");
        appName.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label(session.getUsername());
        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(e -> onLogout.run());

        HBox topBar = new HBox(12, appName, spacer, userLabel, logoutButton);
        topBar.getStyleClass().add("top-bar");
        return topBar;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");

        // Shared admin screens (dependency flows superadmin → admin only)
        addNavButton(sidebar, "Dashboard",
                () -> setCenter(new DashboardScreen(attendanceRepository, employeeRepository)));
        addNavButton(sidebar, "Scan Entry",
                () -> setCenter(new ScanEntryScreen(attendanceRepository, employeeRepository)));
        addNavButton(sidebar, "Employees",
                () -> setCenter(new EmployeesScreen(employeeRepository)));
        addNavButton(sidebar, "Cards",
                () -> setCenter(new CardsScreen(employeeRepository)));
        addNavButton(sidebar, "Working Days",
                () -> setCenter(new WorkingDaysScreen(workingDaysRepository, session)));
        sidebar.getChildren().add(new Separator());
        addNavButton(sidebar, "Statutory Export",
                () -> setCenter(new StatutoryExportScreen(statutoryService, overrideRepository, session)));
        sidebar.getChildren().add(new Separator());

        // Super Admin-only
        addNavButton(sidebar, "OT Switch",
                () -> setCenter(new OtSwitchScreen(session, employeeRepository, otSwitchService)));
        addNavButton(sidebar, "Timestamp Corrections",
                () -> setCenter(new TimestampCorrectionsScreen(
                        session, employeeRepository, attendanceRepository, timestampCorrectionService)));
        addNavButton(sidebar, "Notes",
                () -> setCenter(new NotesScreen(session, employeeRepository, employeeNoteService)));
        return sidebar;
    }

    private void addNavButton(VBox nav, String label, Runnable action) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> action.run());
        nav.getChildren().add(btn);
    }
}
