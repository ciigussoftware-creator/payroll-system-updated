package com.payroll.desktop.ui.superadmin;

import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.admin.CardsScreen;
import com.payroll.desktop.ui.admin.DashboardScreen;
import com.payroll.desktop.ui.admin.EmployeesScreen;
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

    public SuperAdminShell(UserSession session, Runnable onLogout, EmployeeRepository employeeRepository) {
        this.session = session;
        this.onLogout = onLogout;
        this.employeeRepository = employeeRepository;
        setTop(buildTopBar());
        setLeft(buildSidebar());
        setCenter(DashboardScreen.build());
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

        // Shared admin screens (reused — dependency flows superadmin → admin only)
        addNavButton(sidebar, "Dashboard",             () -> setCenter(DashboardScreen.build()));
        addNavButton(sidebar, "Employees",             () -> setCenter(new EmployeesScreen(employeeRepository)));
        addNavButton(sidebar, "Cards",                 () -> setCenter(new CardsScreen(employeeRepository)));
        addNavButton(sidebar, "Working Days",          () -> setCenter(WorkingDaysScreen.build()));
        sidebar.getChildren().add(new Separator());
        addNavButton(sidebar, "Statutory Export",      () -> setCenter(StatutoryExportScreen.build()));
        sidebar.getChildren().add(new Separator());

        // Super Admin-only
        addNavButton(sidebar, "OT Switch",             () -> setCenter(OtSwitchScreen.build()));
        addNavButton(sidebar, "Timestamp Corrections", () -> setCenter(TimestampCorrectionsScreen.build()));
        addNavButton(sidebar, "Notes",                 () -> setCenter(NotesScreen.build()));
        return sidebar;
    }

    private void addNavButton(VBox nav, String label, Runnable action) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> action.run());
        nav.getChildren().add(btn);
    }
}

