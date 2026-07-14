package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.WorkingDaysConfig;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class WorkingDaysScreen extends BorderPane {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final WorkingDaysConfigRepository repository;
    private final UserSession session;

    private final ComboBox<Integer>   yearBox   = new ComboBox<>();
    private final ComboBox<MonthItem> monthBox  = new ComboBox<>();
    private final TextField           daysField = new TextField();
    private final Label               hintLabel = new Label();
    private final Label               errorLabel = new Label();
    private final Label               confirmLabel = new Label();

    private final ObservableList<WorkingDaysConfig> tableData = FXCollections.observableArrayList();

    public WorkingDaysScreen(WorkingDaysConfigRepository repository, UserSession session) {
        this.repository = repository;
        this.session    = session;
        setPadding(new Insets(16));
        setTop(buildFormPanel());
        setCenter(buildHistoryPanel());
        initSelectors();
        refreshTable();
    }

    // ── form panel ─────────────────────────────────────────────────────────────

    private VBox buildFormPanel() {
        Label title = new Label("Working Days Configuration");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Period selector row
        Label periodLbl = new Label("Period:");
        yearBox.setPrefWidth(90);
        monthBox.setPrefWidth(130);
        HBox periodRow = new HBox(8, periodLbl, yearBox, monthBox);
        periodRow.setAlignment(Pos.CENTER_LEFT);

        // Days field row
        Label daysLbl = new Label("Available Working Days:");
        daysField.setPrefWidth(80);
        daysField.setPromptText("e.g. 23");
        HBox daysRow = new HBox(8, daysLbl, daysField);
        daysRow.setAlignment(Pos.CENTER_LEFT);

        hintLabel.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
        errorLabel.setStyle("-fx-text-fill: #cc0000;");
        confirmLabel.setStyle("-fx-text-fill: #2a7f2a; -fx-font-weight: bold;");

        Button saveBtn = new Button("Save");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());

        Separator sep = new Separator();
        sep.setPadding(new Insets(8, 0, 0, 0));

        VBox panel = new VBox(10, title, periodRow, daysRow, hintLabel, errorLabel,
                              new HBox(8, saveBtn, confirmLabel), sep);
        panel.setPadding(new Insets(0, 0, 12, 0));
        return panel;
    }

    // ── history table ──────────────────────────────────────────────────────────

    private VBox buildHistoryPanel() {
        Label histTitle = new Label("Configured Months");
        histTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        var table = new TableView<>(tableData);
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No working days configured yet."));

        TableColumn<WorkingDaysConfig, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(new PropertyValueFactory<>("periodMonth"));
        periodCol.setPrefWidth(100);
        periodCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? null : formatPeriod(val));
            }
        });

        TableColumn<WorkingDaysConfig, Integer> daysCol = new TableColumn<>("Days");
        daysCol.setCellValueFactory(new PropertyValueFactory<>("availableWorkingDays"));
        daysCol.setPrefWidth(60);

        TableColumn<WorkingDaysConfig, Instant> updatedCol = new TableColumn<>("Last Updated");
        updatedCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        updatedCol.setPrefWidth(150);
        updatedCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Instant val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? null : TIMESTAMP_FMT.format(val));
            }
        });

        TableColumn<WorkingDaysConfig, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(new PropertyValueFactory<>("updatedBy"));
        byCol.setPrefWidth(100);

        table.getColumns().addAll(periodCol, daysCol, updatedCol, byCol);

        VBox panel = new VBox(8, histTitle, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    // ── logic ──────────────────────────────────────────────────────────────────

    private void initSelectors() {
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear + 2; y++) {
            yearBox.getItems().add(y);
        }
        yearBox.setValue(currentYear);

        for (Month m : Month.values()) {
            monthBox.getItems().add(new MonthItem(m));
        }
        monthBox.setValue(new MonthItem(LocalDate.now().getMonth()));

        yearBox.setOnAction(e -> loadPeriod());
        monthBox.setOnAction(e -> loadPeriod());

        loadPeriod();
    }

    private void loadPeriod() {
        clearFeedback();
        repository.findByPeriodMonth(currentPeriod()).ifPresentOrElse(
            cfg -> {
                daysField.setText(String.valueOf(cfg.getAvailableWorkingDays()));
                hintLabel.setText("");
            },
            () -> {
                daysField.clear();
                hintLabel.setText("No value set for this month yet.");
            }
        );
    }

    private void save() {
        clearFeedback();
        var result = WorkingDaysValidator.validate(daysField.getText());
        if (!result.valid()) {
            errorLabel.setText(result.error());
            return;
        }
        int days = WorkingDaysValidator.parse(daysField.getText());
        repository.upsert(currentPeriod(), days, session.getUsername());
        hintLabel.setText("");
        confirmLabel.setText("Saved: " + currentMonthDisplay() + " = " + days + " working days");
        refreshTable();
    }

    private void clearFeedback() {
        errorLabel.setText("");
        confirmLabel.setText("");
    }

    private void refreshTable() {
        tableData.setAll(repository.findAll());
    }

    private String currentPeriod() {
        return String.format("%d-%02d", yearBox.getValue(), monthBox.getValue().number());
    }

    private String currentMonthDisplay() {
        return monthBox.getValue().displayName() + " " + yearBox.getValue();
    }

    // ── helper types ───────────────────────────────────────────────────────────

    private static String formatPeriod(String periodMonth) {
        // "2026-06" → "June 2026"
        try {
            String[] parts = periodMonth.split("-");
            int year  = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            return Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + year;
        } catch (Exception e) {
            return periodMonth;
        }
    }

    record MonthItem(Month month) {
        int number()          { return month.getValue(); }
        String displayName()  { return month.getDisplayName(TextStyle.FULL, Locale.getDefault()); }
        @Override public String toString() { return displayName(); }

        @Override public boolean equals(Object o) {
            return o instanceof MonthItem mi && mi.month == this.month;
        }
        @Override public int hashCode() { return month.hashCode(); }
    }
}
