package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class TimestampCorrectionsScreen extends BorderPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserSession session;
    private final EmployeeRepository employeeRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final TimestampCorrectionService correctionService;

    private final ComboBox<Employee> employeeCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Label statusLabel = new Label();
    private final ObservableList<AttendanceRecord> scanItems = FXCollections.observableArrayList();
    private final TableView<AttendanceRecord> scanTable = new TableView<>(scanItems);

    public TimestampCorrectionsScreen(UserSession session,
                                      EmployeeRepository employeeRepo,
                                      AttendanceRecordRepository attendanceRepo,
                                      TimestampCorrectionService correctionService) {
        this.session = session;
        this.employeeRepo = employeeRepo;
        this.attendanceRepo = attendanceRepo;
        this.correctionService = correctionService;

        setTop(buildFilterBar());
        setCenter(buildTableArea());
        setBottom(buildBottomBar());
        setPadding(new Insets(16));

        loadEmployees();
    }

    // ── filter bar ────────────────────────────────────────────────────────────────

    private HBox buildFilterBar() {
        employeeCombo.setPromptText("Select employee…");
        employeeCombo.setMinWidth(220);
        employeeCombo.setCellFactory(lv -> employeeCell());
        employeeCombo.setButtonCell(employeeCell());

        Button loadBtn = new Button("Load Scans");
        loadBtn.setOnAction(e -> loadScans());

        HBox bar = new HBox(12, new Label("Employee:"), employeeCombo,
                new Label("Date:"), datePicker, loadBtn, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 12, 0));
        return bar;
    }

    private ListCell<Employee> employeeCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : e.getEmployeeCode() + " — " + e.getName());
            }
        };
    }

    // ── scan table ────────────────────────────────────────────────────────────────

    private VBox buildTableArea() {
        TableColumn<AttendanceRecord, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(
                        cd.getValue().getScanDatetime().format(DT_FMT)));
        timeCol.setPrefWidth(150);

        TableColumn<AttendanceRecord, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(
                        cd.getValue().getScanType().name()));
        typeCol.setPrefWidth(80);

        TableColumn<AttendanceRecord, String> missingCol = new TableColumn<>("Missing Out?");
        missingCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(isMissingClockOut(cd.getValue()) ? "YES" : ""));
        missingCol.setPrefWidth(95);

        TableColumn<AttendanceRecord, String> origCol = new TableColumn<>("Original Time");
        origCol.setCellValueFactory(cd -> {
            LocalDateTime orig = cd.getValue().getOriginalScanDatetime();
            return new javafx.beans.property.SimpleStringProperty(orig == null ? "" : orig.format(DT_FMT));
        });
        origCol.setPrefWidth(150);

        TableColumn<AttendanceRecord, String> noteCol = new TableColumn<>("Correction Note");
        noteCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(
                        cd.getValue().getCorrectionNote() == null ? "" : cd.getValue().getCorrectionNote()));
        noteCol.setPrefWidth(200);

        TableColumn<AttendanceRecord, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            { editBtn.setOnAction(e -> {
                AttendanceRecord rec = getTableView().getItems().get(getIndex());
                openEditDialog(rec);
            }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : editBtn);
            }
        });

        scanTable.getColumns().setAll(timeCol, typeCol, missingCol, origCol, noteCol, actionCol);
        scanTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        scanTable.setPlaceholder(new Label("Select an employee and date, then click Load Scans."));
        VBox.setVgrow(scanTable, Priority.ALWAYS);
        VBox box = new VBox(scanTable);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    /** A scan is "missing clock-out" if it's the last scan of the day and an ENTRY (odd count). */
    private boolean isMissingClockOut(AttendanceRecord record) {
        if (record.getScanType() != ScanType.ENTRY) return false;
        if (scanItems.isEmpty()) return false;
        AttendanceRecord last = scanItems.get(scanItems.size() - 1);
        return last.getId() != null && last.getId().equals(record.getId())
                && scanItems.size() % 2 == 1;
    }

    // ── bottom bar ────────────────────────────────────────────────────────────────

    private HBox buildBottomBar() {
        Button addBtn = new Button("+ Add Missing Scan");
        addBtn.setOnAction(e -> openAddDialog());

        HBox bar = new HBox(addBtn);
        bar.setPadding(new Insets(12, 0, 0, 0));
        return bar;
    }

    // ── data loading ──────────────────────────────────────────────────────────────

    private void loadEmployees() {
        List<Employee> employees = employeeRepo.findAll();
        employeeCombo.getItems().setAll(employees);
    }

    private void loadScans() {
        Employee emp = employeeCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (emp == null || date == null) {
            statusLabel.setText("Select employee and date first.");
            return;
        }
        List<AttendanceRecord> scans = attendanceRepo.findByEmployeeAndDate(emp, date);
        scanItems.setAll(scans);
        statusLabel.setText(scans.size() + " scan(s) on " + date);
    }

    // ── edit dialog ───────────────────────────────────────────────────────────────

    private void openEditDialog(AttendanceRecord record) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(getScene() == null ? null : getScene().getWindow());
        dialog.setTitle("Edit Scan");

        DatePicker newDatePicker = new DatePicker(record.getScanDatetime().toLocalDate());
        TextField newTimeField = new TextField(record.getScanDatetime().format(TIME_FMT));
        ComboBox<ScanType> typeCombo = new ComboBox<>(
                FXCollections.observableArrayList(ScanType.values()));
        typeCombo.setValue(record.getScanType());
        TextField reasonField = new TextField();
        reasonField.setPromptText("Reason (required)");
        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: red;");

        Button saveBtn = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            String reason = reasonField.getText().trim();
            if (reason.isEmpty()) {
                errLabel.setText("Reason is required.");
                return;
            }
            LocalDateTime newDt;
            try {
                newDt = LocalDateTime.of(newDatePicker.getValue(),
                        java.time.LocalTime.parse(newTimeField.getText().trim(), TIME_FMT));
            } catch (DateTimeParseException | NullPointerException ex) {
                errLabel.setText("Invalid time — use HH:mm format.");
                return;
            }
            ScanType newType = typeCombo.getValue();
            if (newType == null) {
                errLabel.setText("Select a scan type.");
                return;
            }
            try {
                correctionService.correctScan(record.getId(), newDt, newType, reason,
                        session.getUsername());
                dialog.close();
                loadScans();
                statusLabel.setText("Scan corrected.");
            } catch (Exception ex) {
                errLabel.setText("Error: " + ex.getMessage());
            }
        });

        GridPane grid = buildFormGrid(
                new String[]{"New Date:", "New Time (HH:mm):", "Scan Type:", "Reason:"},
                new javafx.scene.Node[]{newDatePicker, newTimeField, typeCombo, reasonField});

        VBox root = new VBox(12, new Label("Edit scan for " + record.getEmployee().getName()),
                grid, errLabel, new HBox(8, saveBtn, cancelBtn));
        root.setPadding(new Insets(20));
        dialog.setScene(new Scene(root, 420, 280));
        dialog.showAndWait();
    }

    // ── add dialog ────────────────────────────────────────────────────────────────

    private void openAddDialog() {
        Employee emp = employeeCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (emp == null || date == null) {
            statusLabel.setText("Load scans for an employee and date first.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(getScene() == null ? null : getScene().getWindow());
        dialog.setTitle("Add Missing Scan");

        DatePicker scanDatePicker = new DatePicker(date);
        TextField timeField = new TextField();
        timeField.setPromptText("HH:mm");
        ComboBox<ScanType> typeCombo = new ComboBox<>(
                FXCollections.observableArrayList(ScanType.values()));
        typeCombo.setPromptText("Type…");
        TextField reasonField = new TextField();
        reasonField.setPromptText("Reason (required)");
        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: red;");

        Button saveBtn = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            String reason = reasonField.getText().trim();
            if (reason.isEmpty()) { errLabel.setText("Reason is required."); return; }
            if (typeCombo.getValue() == null) { errLabel.setText("Select a scan type."); return; }
            LocalDateTime scanDt;
            try {
                scanDt = LocalDateTime.of(scanDatePicker.getValue(),
                        java.time.LocalTime.parse(timeField.getText().trim(), TIME_FMT));
            } catch (DateTimeParseException | NullPointerException ex) {
                errLabel.setText("Invalid time — use HH:mm format.");
                return;
            }
            try {
                correctionService.addMissingScan(emp, scanDt, typeCombo.getValue(), reason,
                        session.getUsername());
                dialog.close();
                loadScans();
                statusLabel.setText("Scan added.");
            } catch (Exception ex) {
                errLabel.setText("Error: " + ex.getMessage());
            }
        });

        GridPane grid = buildFormGrid(
                new String[]{"Date:", "Time (HH:mm):", "Scan Type:", "Reason:"},
                new javafx.scene.Node[]{scanDatePicker, timeField, typeCombo, reasonField});

        VBox root = new VBox(12, new Label("Add scan for " + emp.getName()),
                grid, errLabel, new HBox(8, saveBtn, cancelBtn));
        root.setPadding(new Insets(20));
        dialog.setScene(new Scene(root, 420, 280));
        dialog.showAndWait();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static GridPane buildFormGrid(String[] labels, javafx.scene.Node[] controls) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        for (int i = 0; i < labels.length; i++) {
            grid.add(new Label(labels[i]), 0, i);
            grid.add(controls[i], 1, i);
        }
        return grid;
    }
}
