package com.payroll.desktop.ui.superadmin;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class OtSwitchScreen {

    public static Node build() {
        var pane = new StackPane(new Label("OT Switch — coming in Phase 4D"));
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
