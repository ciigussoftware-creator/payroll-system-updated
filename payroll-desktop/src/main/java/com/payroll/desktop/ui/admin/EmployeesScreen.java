package com.payroll.desktop.ui.admin;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class EmployeesScreen {

    public static Node build() {
        var pane = new StackPane(new Label("Employees — coming in Phase 4B"));
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
