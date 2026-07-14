package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// ISOLATION GUARANTEE: no imports from com.payroll.desktop.ui.superadmin
public class DashboardScreen extends BorderPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DAYS_SHOWN = 7;

    private final DashboardService dashboardService;
    private final EmployeeNoteService noteService;
    private final UserSession session;

    private Label titleLabel;
    private ComboBox<DayOption> dayCombo;
    private Label countInLabel;
    private Label countInCaption;
    private Label countActiveLabel;
    private Label countScannedLabel;
    private Label inSectionTitle;
    private VBox currentlyInList;
    private Label scansTitle;
    private TableView<AttendanceRecord> scansTable;

    public DashboardScreen(DashboardService dashboardService,
                           EmployeeNoteService noteService,
                           UserSession session) {
        this.dashboardService = dashboardService;
        this.noteService = noteService;
        this.session = session;
        setPadding(new Insets(20));
        buildLayout();
        loadData();
    }

    private void buildLayout() {
        titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        dayCombo = new ComboBox<>(FXCollections.observableArrayList(lastSevenDays()));
        dayCombo.setCellFactory(lv -> dayCell());
        dayCombo.setButtonCell(dayCell());
        dayCombo.getSelectionModel().selectFirst();
        dayCombo.setOnAction(e -> loadData());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleBar = new HBox(8, titleLabel, spacer, new Label("Day:"), dayCombo, refreshBtn);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        countInLabel      = summaryValue();
        countActiveLabel  = summaryValue();
        countScannedLabel = summaryValue();
        countInCaption = new Label();
        HBox summary = new HBox(16,
                summaryCard(countInCaption,                     countInLabel),
                summaryCard(new Label("Active Employees"), countActiveLabel),
                summaryCard(new Label("Scanned"),          countScannedLabel));
        summary.setPadding(new Insets(12, 0, 12, 0));

        inSectionTitle = new Label();
        inSectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        currentlyInList = new VBox(4);
        VBox inSection = new VBox(6, inSectionTitle, currentlyInList);
        inSection.setPadding(new Insets(0, 0, 12, 0));

        scansTitle = new Label();
        scansTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        scansTable = buildScansTable();
        VBox.setVgrow(scansTable, Priority.ALWAYS);
        VBox scansSection = new VBox(6, scansTitle, scansTable);
        VBox.setVgrow(scansSection, Priority.ALWAYS);

        VBox top = new VBox(0, titleBar, summary, inSection);
        setTop(top);
        setCenter(scansSection);
    }

    // ── day selector ──────────────────────────────────────────────────────────────

    private record DayOption(LocalDate date, String label) {
    }

    private static List<DayOption> lastSevenDays() {
        LocalDate today = LocalDate.now();
        List<DayOption> days = new ArrayList<>();
        for (int i = 0; i < DAYS_SHOWN; i++) {
            LocalDate d = today.minusDays(i);
            String label = switch (i) {
                case 0  -> "Today (" + d.format(DATE_FMT) + ")";
                case 1  -> "Yesterday (" + d.format(DATE_FMT) + ")";
                default -> d.format(DATE_FMT);
            };
            days.add(new DayOption(d, label));
        }
        return days;
    }

    private static ListCell<DayOption> dayCell() {
        return new ListCell<>() {
            @Override protected void updateItem(DayOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    // ── summary cards ────────────────────────────────────────────────────────────

    private static Label summaryValue() {
        Label lbl = new Label("—");
        lbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        return lbl;
    }

    private static VBox summaryCard(Label caption, Label valueLabel) {
        caption.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        VBox card = new VBox(2, valueLabel, caption);
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle("-fx-background-color: #f4f4f5; -fx-padding: 12; -fx-background-radius: 8;");
        card.setMinWidth(140);
        return card;
    }

    // ── scans table ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<AttendanceRecord> buildScansTable() {
        TableView<AttendanceRecord> table = new TableView<>();
        table.setPlaceholder(new Label("No scans recorded for this day."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AttendanceRecord, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getScanDatetime().format(TIME_FMT)));
        timeCol.setPrefWidth(80);

        TableColumn<AttendanceRecord, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmployee().getEmployeeCode()));
        codeCol.setPrefWidth(110);

        TableColumn<AttendanceRecord, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmployee().getName()));
        nameCol.setPrefWidth(200);

        TableColumn<AttendanceRecord, String> dirCol = new TableColumn<>("Direction");
        dirCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getScanType().name()));
        dirCol.setPrefWidth(100);

        table.getColumns().addAll(timeCol, codeCol, nameCol, dirCol, buildAddNoteColumn());
        return table;
    }

    private TableColumn<AttendanceRecord, Void> buildAddNoteColumn() {
        TableColumn<AttendanceRecord, Void> col = new TableColumn<>("Note");
        col.setPrefWidth(100);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button addNoteBtn = new Button("Add Note");
            {
                addNoteBtn.setOnAction(e -> {
                    AttendanceRecord r = getTableView().getItems().get(getIndex());
                    openAddNoteDialog(r);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : addNoteBtn);
            }
        });
        return col;
    }

    // ── add note dialog ──────────────────────────────────────────────────────────

    private void openAddNoteDialog(AttendanceRecord record) {
        Employee emp = record.getEmployee();
        LocalDate noteDate = record.getScanDatetime().toLocalDate();

        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Add Note");
        dialog.setHeaderText("Add note for " + emp.getEmployeeCode() + " — " + emp.getName()
                + " on " + noteDate.format(DATE_FMT));

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextArea textArea = new TextArea();
        textArea.setPromptText("Enter note text…");
        textArea.setPrefRowCount(4);
        textArea.setPrefWidth(320);
        textArea.setWrapText(true);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #cc0000;");
        errorLabel.setWrapText(true);

        VBox content = new VBox(8, textArea, errorLabel);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (textArea.getText() == null || textArea.getText().isBlank()) {
                errorLabel.setText("Note text cannot be empty.");
                event.consume();
            }
        });

        dialog.setResultConverter(btn -> btn == saveButtonType ? textArea.getText().trim() : null);

        dialog.showAndWait().ifPresent(text -> {
            noteService.addNote(emp.getId(), noteDate, text, session.getUsername());

            Alert confirm = new Alert(Alert.AlertType.INFORMATION,
                    "Note saved for " + emp.getName() + " on " + noteDate.format(DATE_FMT) + ".");
            confirm.initOwner(getScene().getWindow());
            confirm.setHeaderText(null);
            confirm.showAndWait();
        });
    }

    // ── data ─────────────────────────────────────────────────────────────────────

    private void loadData() {
        DayOption selected = dayCombo.getValue();
        LocalDate date = selected.date();
        boolean isToday = date.equals(LocalDate.now());

        DashboardSnapshot snapshot = dashboardService.loadFor(date);

        titleLabel.setText("Dashboard — Attendance for " + selected.label());
        scansTitle.setText("Scans — " + selected.label());
        inSectionTitle.setText(isToday ? "Currently In" : "Missing Clock-Out");
        countInCaption.setText(isToday ? "Currently In" : "Missing Clock-Out");

        countInLabel.setText(String.valueOf(snapshot.lastScanIsEntry().size()));
        countActiveLabel.setText(String.valueOf(snapshot.activeEmployeeCount()));
        countScannedLabel.setText(String.valueOf(snapshot.scannedEmployeeCount()));

        currentlyInList.getChildren().clear();
        if (snapshot.lastScanIsEntry().isEmpty()) {
            currentlyInList.getChildren().add(new Label(
                    isToday ? "No employees currently in." : "No missing clock-outs for this day."));
        } else {
            String verb = isToday ? "clocked in at" : "last scanned in at";
            for (AttendanceRecord r : snapshot.lastScanIsEntry()) {
                currentlyInList.getChildren().add(new Label(
                        r.getEmployee().getEmployeeCode() + "  " + r.getEmployee().getName()
                        + " — " + verb + " " + r.getScanDatetime().format(TIME_FMT)));
            }
        }

        scansTable.setItems(FXCollections.observableArrayList(snapshot.scans()));
    }
}
