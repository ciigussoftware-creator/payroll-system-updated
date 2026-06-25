package com.payroll.desktop.ui.shell;

import com.payroll.desktop.ui.admin.CardsScreen;
import com.payroll.desktop.ui.admin.DashboardScreen;
import com.payroll.desktop.ui.admin.EmployeesScreen;
import com.payroll.desktop.ui.admin.StatutoryExportScreen;
import com.payroll.desktop.ui.admin.WorkingDaysScreen;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

// ISOLATION GUARANTEE: this class and the entire ui.admin package must have
// zero imports from com.payroll.desktop.ui.superadmin. Enforced by AdminPackageIsolationTest.
public class AdminShell extends BorderPane {

    private final UserSession session;
    private final Runnable onLogout;

    public AdminShell(UserSession session, Runnable onLogout) {
        this.session = session;
        this.onLogout = onLogout;
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

        addNavButton(sidebar, "Dashboard",        () -> setCenter(DashboardScreen.build()));
        addNavButton(sidebar, "Employees",        () -> setCenter(EmployeesScreen.build()));
        addNavButton(sidebar, "Cards",            () -> setCenter(CardsScreen.build()));
        addNavButton(sidebar, "Working Days",     () -> setCenter(WorkingDaysScreen.build()));
        sidebar.getChildren().add(new Separator());
        addNavButton(sidebar, "Statutory Export", () -> setCenter(StatutoryExportScreen.build()));
        return sidebar;
    }

    private void addNavButton(VBox nav, String label, Runnable action) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> action.run());
        nav.getChildren().add(btn);
    }
}

