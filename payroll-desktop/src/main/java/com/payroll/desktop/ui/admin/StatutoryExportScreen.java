package com.payroll.desktop.ui.admin;

import com.payroll.desktop.repository.StatutoryOverrideRepository;
import com.payroll.desktop.statutory.StatutoryCalculationService;
import com.payroll.desktop.statutory.StatutoryExcelExporter;
import com.payroll.desktop.statutory.StatutoryFlag;
import com.payroll.desktop.statutory.StatutoryRow;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

// ISOLATION GUARANTEE: no imports from com.payroll.desktop.ui.superadmin
public class StatutoryExportScreen extends BorderPane {

    private final StatutoryCalculationService service;
    private final StatutoryOverrideRepository overrideRepo;
    private final UserSession session;

    private final ComboBox<Integer>   yearBox  = new ComboBox<>();
    private final ComboBox<MonthItem> monthBox = new ComboBox<>();

    private final ObservableList<StatutoryRow> tableData = FXCollections.observableArrayList();
    private String currentPeriod = null;

    public StatutoryExportScreen(StatutoryCalculationService service,
                                  StatutoryOverrideRepository overrideRepo,
                                  UserSession session) {
        this.service = service;
        this.overrideRepo = overrideRepo;
        this.session = session;
        setPadding(new Insets(16));
        setTop(buildToolbar());
        setCenter(buildTableSection());
        setBottom(buildBottomBar());
    }

    // ── toolbar ────────────────────────────────────────────────────────────────

    private VBox buildToolbar() {
        Label title = new Label("Statutory Export");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear + 2; y++) yearBox.getItems().add(y);
        yearBox.setValue(currentYear);
        yearBox.setPrefWidth(90);

        for (Month m : Month.values()) monthBox.getItems().add(new MonthItem(m));
        monthBox.setValue(new MonthItem(LocalDate.now().getMonth()));
        monthBox.setPrefWidth(140);

        Button computeBtn = new Button("Compute");
        computeBtn.setDefaultButton(true);
        computeBtn.setOnAction(e -> compute());

        HBox selectorRow = new HBox(8,
                new Label("Year:"), yearBox,
                new Label("Month:"), monthBox,
                computeBtn);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        selectorRow.setPadding(new Insets(8, 0, 8, 0));

        VBox toolbar = new VBox(4, title, selectorRow);
        toolbar.setPadding(new Insets(0, 0, 8, 0));
        return toolbar;
    }

    // ── table ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private VBox buildTableSection() {
        TableView<StatutoryRow> table = new TableView<>(tableData);
        table.setPlaceholder(new Label("Select a month and click Compute."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(StatutoryRow item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null && item.hasFlag(StatutoryFlag.WORKING_DAYS_NOT_SET)) {
                    setStyle("-fx-background-color: #fff3cd;");
                } else {
                    setStyle("");
                }
            }
        });

        TableColumn<StatutoryRow, String> codeCol = new TableColumn<>("Employee Code");
        codeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().employeeCode()));
        codeCol.setPrefWidth(120);

        TableColumn<StatutoryRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(180);

        TableColumn<StatutoryRow, String> availCol = numericCol("Available Days",
                r -> r.availableWorkingDays() == 0 ? "—" : String.valueOf(r.availableWorkingDays()));
        availCol.setPrefWidth(110);

        TableColumn<StatutoryRow, String> compDaysCol = numericCol("Computed Days",
                r -> formatDays(r.computedDaysWorked()));

        TableColumn<StatutoryRow, String> effDaysCol = numericCol("Effective Days",
                r -> formatDays(r.effectiveDaysWorked()));

        TableColumn<StatutoryRow, String> grossCol = numericCol("Gross",
                r -> formatMoney(r.gross()));
        grossCol.setPrefWidth(130);

        TableColumn<StatutoryRow, String> epf8Col = numericCol("EPF 8%",
                r -> formatMoney(r.epfEmployee()));

        TableColumn<StatutoryRow, String> epf12Col = numericCol("EPF 12%",
                r -> formatMoney(r.epfEmployer()));

        TableColumn<StatutoryRow, String> etfCol = numericCol("ETF 3%",
                r -> formatMoney(r.etf()));

        TableColumn<StatutoryRow, String> balanceCol = numericCol("Admin Balance",
                r -> formatMoney(r.adminBalance()));
        balanceCol.setPrefWidth(130);

        TableColumn<StatutoryRow, StatutoryRow> overrideCol = new TableColumn<>("Override");
        overrideCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        overrideCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Override");
            {
                btn.setOnAction(e -> {
                    StatutoryRow row = getItem();
                    if (row != null) showOverrideDialog(row);
                });
            }
            @Override
            protected void updateItem(StatutoryRow item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : btn);
            }
        });
        overrideCol.setPrefWidth(90);

        table.getColumns().addAll(
                codeCol, nameCol, availCol, compDaysCol, effDaysCol,
                grossCol, epf8Col, epf12Col, etfCol, balanceCol, overrideCol);

        VBox.setVgrow(table, Priority.ALWAYS);
        return new VBox(table);
    }

    // ── bottom bar ─────────────────────────────────────────────────────────────

    private HBox buildBottomBar() {
        Button exportBtn = new Button("Export to Excel");
        exportBtn.setOnAction(e -> exportToExcel());
        HBox bar = new HBox(exportBtn);
        bar.setPadding(new Insets(8, 0, 0, 0));
        return bar;
    }

    // ── actions ────────────────────────────────────────────────────────────────

    private void compute() {
        int year  = yearBox.getValue();
        int month = monthBox.getValue().number();
        currentPeriod = String.format("%04d-%02d", year, month);
        List<StatutoryRow> rows = service.computeForMonth(currentPeriod);
        tableData.setAll(rows);
    }

    private void exportToExcel() {
        if (currentPeriod == null || tableData.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Nothing to export",
                    "Compute the results first.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Statutory Export");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel files (*.xlsx)", "*.xlsx"));
        fc.setInitialFileName("statutory_" + currentPeriod + ".xlsx");
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try {
            new StatutoryExcelExporter().export(tableData, currentPeriod, file.toPath());
            showAlert(Alert.AlertType.INFORMATION, "Export complete",
                    "Saved to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Export failed", ex.getMessage());
        }
    }

    private void showOverrideDialog(StatutoryRow row) {
        Stage dialog = new Stage();
        dialog.initOwner(getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Override Days Worked — " + row.name());

        Label daysLbl = new Label("Effective Days Worked:");
        TextField daysField = new TextField(row.effectiveDaysWorked().stripTrailingZeros().toPlainString());

        Label reasonLbl = new Label("Reason (required):");
        TextField reasonField = new TextField(row.overrideReason() != null ? row.overrideReason() : "");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #cc0000;");

        Button saveBtn = new Button("Save");
        saveBtn.setDefaultButton(true);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            String reason = reasonField.getText().trim();
            if (reason.isEmpty()) {
                errorLbl.setText("Reason is required");
                return;
            }
            BigDecimal newDays;
            try {
                newDays = new BigDecimal(daysField.getText().trim());
                if (newDays.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                errorLbl.setText("Enter a valid number (≥ 0)");
                return;
            }
            overrideRepo.upsert(row.employeeId(), currentPeriod, newDays, reason, session.getUsername());
            dialog.close();
            compute();
        });

        VBox content = new VBox(10,
                daysLbl, daysField,
                reasonLbl, reasonField,
                errorLbl,
                new HBox(8, saveBtn, cancelBtn));
        content.setPadding(new Insets(16));
        content.setMinWidth(380);
        dialog.setScene(new Scene(content));
        dialog.showAndWait();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static <T> TableColumn<T, String> numericCol(String header,
                                                          java.util.function.Function<T, String> fmt) {
        TableColumn<T, String> col = new TableColumn<>(header);
        col.setCellValueFactory(c -> new SimpleStringProperty(fmt.apply(c.getValue())));
        col.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        col.setPrefWidth(100);
        return col;
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) return "";
        return String.format("Rs. %,.2f", value);
    }

    private static String formatDays(BigDecimal value) {
        if (value == null) return "";
        String s = value.stripTrailingZeros().toPlainString();
        return s.isEmpty() ? "0" : s;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ── helper record ──────────────────────────────────────────────────────────

    record MonthItem(Month month) {
        int number() { return month.getValue(); }
        @Override
        public String toString() {
            return month.getDisplayName(TextStyle.FULL, Locale.getDefault());
        }
        @Override
        public boolean equals(Object o) {
            return o instanceof MonthItem mi && mi.month == this.month;
        }
        @Override
        public int hashCode() { return month.hashCode(); }
    }
}
