package com.payroll.desktop.ui.admin;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class DashboardScreen {

    public static Node build() {
        var pane = new StackPane(new Label("Dashboard — coming in Phase 4B"));
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
