package com.payroll.desktop.ui.admin;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class CardsScreen {

    public static Node build() {
        var pane = new StackPane(new Label("Cards — coming in Phase 4C"));
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
