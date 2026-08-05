package com.payroll.desktop.ui.shell;

import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.AuditLogRepository;
import com.payroll.desktop.repository.EmployeeNoteRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import com.payroll.desktop.statutory.StatutoryCalculationService;
import com.payroll.desktop.sync.SyncScheduler;
import com.payroll.desktop.ui.admin.DashboardScreen;
import com.payroll.desktop.ui.admin.DashboardService;
import com.payroll.desktop.ui.admin.EmployeeManagementService;
import com.payroll.desktop.ui.admin.EmployeeNoteService;
import com.payroll.desktop.ui.admin.EmployeesScreen;
import com.payroll.desktop.ui.admin.ScanEntryScreen;
import com.payroll.desktop.ui.admin.StatutoryExportScreen;
import com.payroll.desktop.ui.admin.WorkingDaysScreen;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

// ISOLATION GUARANTEE: this class and the entire ui.admin package must have
// zero imports from com.payroll.desktop.ui.superadmin. Enforced by AdminPackageIsolationTest.
public class AdminShell extends BorderPane {

    private final UserSession session;
    private final Runnable onLogout;
    private final EmployeeRepository employeeRepository;
    private final WorkingDaysConfigRepository workingDaysRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final StatutoryCalculationService statutoryService;
    private final StatutoryOverrideRepository overrideRepository;
    private final EmployeeManagementService employeeManagementService;
    private final EmployeeNoteService employeeNoteService;
    private final DashboardService dashboardService;
    private final SyncScheduler syncScheduler;

    public AdminShell(UserSession session,
                      Runnable onLogout,
                      EmployeeRepository employeeRepository,
                      WorkingDaysConfigRepository workingDaysRepository,
                      AttendanceRecordRepository attendanceRepository,
                      StatutoryCalculationService statutoryService,
                      StatutoryOverrideRepository overrideRepository,
                      AuditLogRepository auditLogRepository,
                      EmployeeNoteRepository employeeNoteRepository,
                      SyncScheduler syncScheduler) {
        this.session = session;
        this.onLogout = onLogout;
        this.employeeRepository = employeeRepository;
        this.workingDaysRepository = workingDaysRepository;
        this.attendanceRepository = attendanceRepository;
        this.statutoryService = statutoryService;
        this.overrideRepository = overrideRepository;
        this.employeeManagementService = new EmployeeManagementService(
                employeeRepository, attendanceRepository, auditLogRepository);
        this.employeeNoteService = new EmployeeNoteService(employeeNoteRepository, auditLogRepository);
        this.dashboardService = new DashboardService(attendanceRepository, employeeRepository);
        this.syncScheduler = syncScheduler;
        setTop(buildTopBar());
        setLeft(buildSidebar());
        setCenter(new DashboardScreen(dashboardService, employeeNoteService, session, syncScheduler));
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

        addNavButton(sidebar, "Dashboard",
                () -> setCenter(new DashboardScreen(dashboardService, employeeNoteService, session, syncScheduler)));
        addNavButton(sidebar, "Scan Entry",
                () -> setCenter(new ScanEntryScreen(attendanceRepository, employeeRepository)));
        addNavButton(sidebar, "Employees",
                () -> setCenter(new EmployeesScreen(employeeRepository, employeeManagementService, session)));
        addNavButton(sidebar, "Working Days",
                () -> setCenter(new WorkingDaysScreen(workingDaysRepository, session)));
        sidebar.getChildren().add(new Separator());
        addNavButton(sidebar, "Statutory Export",
                () -> setCenter(new StatutoryExportScreen(statutoryService, overrideRepository, session)));
        return sidebar;
    }

    private void addNavButton(VBox nav, String label, Runnable action) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> action.run());
        nav.getChildren().add(btn);
    }
}
