package com.payroll.desktop.ui.admin;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class WorkingDaysScreen {

    public static Node build() {
        var pane = new StackPane(new Label("Working Days — coming in Phase 4C"));
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
