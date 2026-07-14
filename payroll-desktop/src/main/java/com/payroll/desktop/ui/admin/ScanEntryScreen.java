package com.payroll.desktop.ui.admin;

import com.payroll.desktop.attendance.ScanIngestionService;
import com.payroll.desktop.attendance.ScanResult;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// ISOLATION GUARANTEE: no imports from com.payroll.desktop.ui.superadmin
public class ScanEntryScreen extends BorderPane {

    private static final DateTimeFormatter DT_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter LOG_FMT  = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ScanIngestionService ingestionService;

    private TextField cardField;
    private Label feedbackLabel;
    private TextField overrideField;
    private ObservableList<String> logItems;

    public ScanEntryScreen(AttendanceRecordRepository attendanceRepo,
                           EmployeeRepository employeeRepo) {
        this.ingestionService = new ScanIngestionService(attendanceRepo, employeeRepo);
        setPadding(new Insets(20));
        buildLayout();
    }

    private void buildLayout() {
        Label title = new Label("Scan Entry");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        cardField = new TextField();
        cardField.setPromptText("Scan / enter card number");
        cardField.setPrefWidth(320);
        cardField.setOnAction(e -> recordScan());

        Button recordBtn = new Button("Record");
        recordBtn.setOnAction(e -> recordScan());

        HBox inputRow = new HBox(8, cardField, recordBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        feedbackLabel = new Label("");
        feedbackLabel.setWrapText(true);
        feedbackLabel.setStyle("-fx-font-size: 14px;");
        feedbackLabel.setMinHeight(24);

        Label overrideWarning = new Label(
                "[DEV] Date/time override — leave blank for now (yyyy-MM-dd HH:mm):");
        overrideWarning.setStyle("-fx-text-fill: #b45309; -fx-font-size: 11px;");
        overrideField = new TextField();
        overrideField.setPromptText("yyyy-MM-dd HH:mm  (blank = now)");
        overrideField.setPrefWidth(220);
        HBox overrideRow = new HBox(8, overrideWarning, overrideField);
        overrideRow.setAlignment(Pos.CENTER_LEFT);
        overrideRow.setStyle("-fx-background-color: #fef9c3; -fx-padding: 8; -fx-background-radius: 4;");

        VBox topSection = new VBox(10, title, inputRow, feedbackLabel, overrideRow);
        topSection.setPadding(new Insets(0, 0, 16, 0));
        setTop(topSection);

        logItems = FXCollections.observableArrayList();
        Label logTitle = new Label("Session Log");
        logTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        ListView<String> logView = new ListView<>(logItems);
        logView.setPlaceholder(new Label("No scans recorded yet this session."));
        VBox.setVgrow(logView, Priority.ALWAYS);
        VBox logSection = new VBox(6, logTitle, logView);
        VBox.setVgrow(logSection, Priority.ALWAYS);
        setCenter(logSection);
    }

    private void recordScan() {
        String card = cardField.getText().trim();
        if (card.isEmpty()) {
            cardField.requestFocus();
            return;
        }

        LocalDateTime when = resolveWhen();
        if (when == null) {
            feedbackLabel.setText("Invalid override format — use yyyy-MM-dd HH:mm.");
            feedbackLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #dc2626;");
            return;
        }

        ScanResult result = ingestionService.recordScan(card, when);
        cardField.clear();
        cardField.requestFocus();

        switch (result.outcome()) {
            case ACCEPTED -> {
                String msg = result.employeeCode() + " " + result.employeeName()
                        + " — clocked " + result.scanType().name()
                        + " at " + result.time().format(TIME_FMT);
                feedbackLabel.setText(msg);
                feedbackLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #16a34a;");
                logItems.add(0, result.time().format(LOG_FMT) + "  |  "
                        + result.employeeCode() + " " + result.employeeName()
                        + "  |  " + result.scanType().name() + "  |  ACCEPTED");
            }
            case IGNORED_TOO_SOON -> {
                feedbackLabel.setText("Ignored: duplicate tap within 30 min");
                feedbackLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #d97706;");
                logItems.add(0, when.format(LOG_FMT) + "  |  card:" + card
                        + "  |  —  |  IGNORED_TOO_SOON");
            }
            case REJECTED_UNKNOWN_CARD -> {
                feedbackLabel.setText("Unknown or inactive card: " + result.cardNumber());
                feedbackLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #dc2626;");
                logItems.add(0, when.format(LOG_FMT) + "  |  card:" + card
                        + "  |  —  |  REJECTED_UNKNOWN_CARD");
            }
        }
    }

    private LocalDateTime resolveWhen() {
        String override = overrideField.getText().trim();
        if (override.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(override, DT_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
