package com.payroll.desktop.ui.admin;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class StatutoryExportScreen {

    public static Node build() {
        var pane = new StackPane(new Label("Statutory Export — coming in Phase 4D"));
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
