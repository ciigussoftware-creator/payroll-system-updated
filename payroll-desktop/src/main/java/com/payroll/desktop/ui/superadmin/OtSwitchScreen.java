package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.DayType;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class OtSwitchScreen extends BorderPane {

    private final UserSession session;
    private final EmployeeRepository employeeRepository;
    private final OtSwitchService otSwitchService;

    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TextField reasonField = new TextField();
    private final VBox retroBox = new VBox(6);

    private final ComboBox<DayType> dayTypeCombo = new ComboBox<>();
    private final CheckBox allStaffOtCheck = new CheckBox("All Staff OT");
    private final Label dayLevelStatus = new Label();

    private final ObservableList<EmployeeAuthRow> employeeRows = FXCollections.observableArrayList();
    private final Label employeeStatus = new Label();
    private Map<Long, Boolean> originalAuthorized = new HashMap<>();

    public OtSwitchScreen(UserSession session,
                          EmployeeRepository employeeRepository,
                          OtSwitchService otSwitchService) {
        this.session = session;
        this.employeeRepository = employeeRepository;
        this.otSwitchService = otSwitchService;
        setPadding(new Insets(16));
        buildUI();
        loadForDate(LocalDate.now());
        datePicker.setOnAction(e -> loadForDate(datePicker.getValue()));
    }

    private void buildUI() {
        Label title = new Label("OT Switch");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        HBox dateRow = new HBox(8, new Label("Date:"), datePicker);
        dateRow.setAlignment(Pos.CENTER_LEFT);

        Label retroLabel = new Label("Retroactive change — reason is required:");
        retroLabel.setStyle("-fx-text-fill: #b55000; -fx-font-weight: bold;");
        reasonField.setPromptText("Enter reason for retroactive change");
        retroBox.getChildren().addAll(retroLabel, reasonField);
        retroBox.setVisible(false);
        retroBox.setManaged(false);

        VBox content = new VBox(20, title, dateRow, retroBox,
                buildDayLevelSection(), buildEmployeeSection());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        setCenter(scroll);
    }

    private VBox buildDayLevelSection() {
        Label sectionTitle = new Label("Day-Level OT Configuration");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        dayTypeCombo.getItems().addAll(DayType.values());
        dayTypeCombo.setValue(DayType.WEEKDAY);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.add(new Label("Day Type:"), 0, 0);
        grid.add(dayTypeCombo, 1, 0);
        grid.add(new Label("All Staff OT:"), 0, 1);
        grid.add(allStaffOtCheck, 1, 1);

        Button saveBtn = new Button("Save Day-Level Config");
        saveBtn.setOnAction(e -> saveDayLevel());

        dayLevelStatus.setWrapText(true);

        HBox actionRow = new HBox(12, saveBtn, dayLevelStatus);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, sectionTitle, grid, actionRow);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 12;");
        return box;
    }

    @SuppressWarnings("unchecked")
    private VBox buildEmployeeSection() {
        Label sectionTitle = new Label("Per-Employee OT Authorization");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TableView<EmployeeAuthRow> table = new TableView<>(employeeRows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(300);
        table.setPlaceholder(new Label("No active employees."));

        TableColumn<EmployeeAuthRow, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(f ->
                new javafx.beans.property.SimpleStringProperty(f.getValue().getEmployeeCode()));
        codeCol.setPrefWidth(100);

        TableColumn<EmployeeAuthRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(f ->
                new javafx.beans.property.SimpleStringProperty(f.getValue().getEmployeeName()));
        nameCol.setPrefWidth(200);

        TableColumn<EmployeeAuthRow, Boolean> authCol = new TableColumn<>("OT Authorized");
        authCol.setCellValueFactory(f -> f.getValue().authorizedProperty().asObject());
        authCol.setCellFactory(CheckBoxTableCell.forTableColumn(
                i -> (i >= 0 && i < employeeRows.size()) ? employeeRows.get(i).authorizedProperty()
                                                          : new SimpleBooleanProperty(false)));
        authCol.setEditable(true);
        authCol.setPrefWidth(120);

        table.getColumns().addAll(codeCol, nameCol, authCol);

        Button saveBtn = new Button("Save Employee Authorizations");
        saveBtn.setOnAction(e -> saveEmployeeAuthorizations());

        employeeStatus.setWrapText(true);

        HBox actionRow = new HBox(12, saveBtn, employeeStatus);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, sectionTitle, table, actionRow);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 12;");
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void loadForDate(LocalDate date) {
        if (date == null) return;
        clearFeedback();

        boolean isRetro = date.isBefore(LocalDate.now());
        retroBox.setVisible(isRetro);
        retroBox.setManaged(isRetro);
        if (!isRetro) reasonField.clear();

        loadDayLevelConfig(date);
        loadEmployeeAuthorizations(date);
    }

    private void loadDayLevelConfig(LocalDate date) {
        boolean isSunday = date.getDayOfWeek() == DayOfWeek.SUNDAY;
        dayTypeCombo.setValue(isSunday ? DayType.SUNDAY : DayType.WEEKDAY);
        allStaffOtCheck.setSelected(isSunday);

        otSwitchService.loadDayConfig(date).ifPresent(config -> {
            dayTypeCombo.setValue(config.getDayType());
            allStaffOtCheck.setSelected(config.isAllStaffOt());
        });
    }

    private void loadEmployeeAuthorizations(LocalDate date) {
        originalAuthorized = new HashMap<>();
        employeeRows.clear();

        Map<Long, Boolean> authMap = new HashMap<>();
        for (var auth : otSwitchService.loadEmployeeAuthorizations(date)) {
            authMap.put(auth.getEmployeeId(), auth.isAuthorized());
        }

        for (var emp : employeeRepository.findAllActive()) {
            boolean auth = authMap.getOrDefault(emp.getId(), false);
            originalAuthorized.put(emp.getId(), auth);
            employeeRows.add(new EmployeeAuthRow(emp.getId(), emp.getEmployeeCode(), emp.getName(), auth));
        }
    }

    private void saveDayLevel() {
        LocalDate date = datePicker.getValue();
        if (date == null) return;
        String reason = reasonField.getText().trim();
        try {
            otSwitchService.saveDayLevel(date, dayTypeCombo.getValue(),
                    allStaffOtCheck.isSelected(), session.getUsername(), reason);
            dayLevelStatus.setText("Saved.");
            dayLevelStatus.setStyle("-fx-text-fill: #2a7f2a;");
        } catch (IllegalArgumentException ex) {
            dayLevelStatus.setText(ex.getMessage());
            dayLevelStatus.setStyle("-fx-text-fill: #cc0000;");
        }
    }

    private void saveEmployeeAuthorizations() {
        LocalDate date = datePicker.getValue();
        if (date == null) return;
        String reason = reasonField.getText().trim();

        int savedCount = 0;
        for (EmployeeAuthRow row : employeeRows) {
            boolean original = originalAuthorized.getOrDefault(row.getEmployeeId(), false);
            if (original == row.isAuthorized()) continue;
            try {
                otSwitchService.saveEmployeeAuthorization(date, row.getEmployeeId(),
                        row.isAuthorized(), row.getEmployeeCode(), session.getUsername(), reason);
                savedCount++;
            } catch (IllegalArgumentException ex) {
                employeeStatus.setText(ex.getMessage());
                employeeStatus.setStyle("-fx-text-fill: #cc0000;");
                return;
            }
        }
        for (EmployeeAuthRow row : employeeRows) {
            originalAuthorized.put(row.getEmployeeId(), row.isAuthorized());
        }
        String msg = savedCount == 0 ? "No changes to save." : "Saved " + savedCount + " authorization(s).";
        employeeStatus.setText(msg);
        employeeStatus.setStyle("-fx-text-fill: #2a7f2a;");
    }

    private void clearFeedback() {
        dayLevelStatus.setText("");
        employeeStatus.setText("");
    }

    // ── row model ─────────────────────────────────────────────────────────────────

    static final class EmployeeAuthRow {
        private final long employeeId;
        private final String employeeCode;
        private final String employeeName;
        private final SimpleBooleanProperty authorized;

        EmployeeAuthRow(long employeeId, String code, String name, boolean auth) {
            this.employeeId = employeeId;
            this.employeeCode = code;
            this.employeeName = name;
            this.authorized = new SimpleBooleanProperty(auth);
        }

        long getEmployeeId() { return employeeId; }
        String getEmployeeCode() { return employeeCode; }
        String getEmployeeName() { return employeeName; }
        BooleanProperty authorizedProperty() { return authorized; }
        boolean isAuthorized() { return authorized.get(); }
    }
}
