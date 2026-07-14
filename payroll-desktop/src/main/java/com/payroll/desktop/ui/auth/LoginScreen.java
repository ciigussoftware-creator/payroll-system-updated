package com.payroll.desktop.ui.auth;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class LoginScreen extends VBox {

    public LoginScreen(AuthService authService, Consumer<UserSession> onLoginSuccess) {
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(48));

        Label title = new Label("Payroll System");
        title.getStyleClass().add("auth-title");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(280);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(280);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");

        Button loginButton = new Button("Login");
        loginButton.setDefaultButton(true);
        loginButton.setPrefWidth(280);
        loginButton.setOnAction(e -> {
            var session = authService.authenticate(usernameField.getText(), passwordField.getText());
            if (session.isPresent()) {
                onLoginSuccess.accept(session.get());
            } else {
                errorLabel.setText("Invalid username or password.");
                passwordField.clear();
            }
        });

        getChildren().addAll(title, usernameField, passwordField, loginButton, errorLabel);
    }
}
