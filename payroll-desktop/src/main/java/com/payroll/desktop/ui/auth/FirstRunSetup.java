package com.payroll.desktop.ui.auth;

import com.payroll.core.entity.UserAccount;
import com.payroll.core.entity.UserRole;
import com.payroll.desktop.repository.UserAccountRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class FirstRunSetup extends VBox {

    public FirstRunSetup(UserAccountRepository repo, PasswordHasher hasher, Runnable onSetupComplete) {
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setPadding(new Insets(40));
        setMaxWidth(360);

        Label title = new Label("Initial Setup");
        title.getStyleClass().add("setup-title");

        Label subtitle = new Label("Create your Admin and Super Admin accounts to get started.");
        subtitle.setWrapText(true);

        Label adminSection = new Label("Admin Account");
        adminSection.getStyleClass().add("section-label");
        TextField adminUsername = new TextField();
        adminUsername.setPromptText("Admin username");
        PasswordField adminPassword = new PasswordField();
        adminPassword.setPromptText("Admin password");
        PasswordField adminConfirm = new PasswordField();
        adminConfirm.setPromptText("Confirm admin password");

        Label superSection = new Label("Super Admin Account");
        superSection.getStyleClass().add("section-label");
        TextField superUsername = new TextField();
        superUsername.setPromptText("Super admin username");
        PasswordField superPassword = new PasswordField();
        superPassword.setPromptText("Super admin password");
        PasswordField superConfirm = new PasswordField();
        superConfirm.setPromptText("Confirm super admin password");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setWrapText(true);

        Button createButton = new Button("Create Accounts");
        createButton.setDefaultButton(true);
        createButton.setMaxWidth(Double.MAX_VALUE);
        createButton.setOnAction(e -> {
            String aUser = adminUsername.getText().trim();
            String aPass = adminPassword.getText();
            String sUser = superUsername.getText().trim();
            String sPass = superPassword.getText();

            if (aUser.isEmpty() || aPass.isEmpty() || sUser.isEmpty() || sPass.isEmpty()) {
                errorLabel.setText("All fields are required.");
                return;
            }
            if (!aPass.equals(adminConfirm.getText())) {
                errorLabel.setText("Admin passwords do not match.");
                return;
            }
            if (!sPass.equals(superConfirm.getText())) {
                errorLabel.setText("Super Admin passwords do not match.");
                return;
            }
            if (aUser.equals(sUser)) {
                errorLabel.setText("Admin and Super Admin must have different usernames.");
                return;
            }

            UserAccount admin = new UserAccount();
            admin.setUsername(aUser);
            admin.setPasswordHash(hasher.hash(aPass));
            admin.setRole(UserRole.ADMIN);
            repo.save(admin);

            UserAccount superAdmin = new UserAccount();
            superAdmin.setUsername(sUser);
            superAdmin.setPasswordHash(hasher.hash(sPass));
            superAdmin.setRole(UserRole.SUPER_ADMIN);
            repo.save(superAdmin);

            onSetupComplete.run();
        });

        getChildren().addAll(
                title, subtitle, new Separator(),
                adminSection, adminUsername, adminPassword, adminConfirm,
                new Separator(),
                superSection, superUsername, superPassword, superConfirm,
                new Separator(),
                createButton, errorLabel
        );
    }
}
