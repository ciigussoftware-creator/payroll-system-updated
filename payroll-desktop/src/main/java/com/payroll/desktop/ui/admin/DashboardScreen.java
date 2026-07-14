package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// ISOLATION GUARANTEE: no imports from com.payroll.desktop.ui.superadmin
public class DashboardScreen extends BorderPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRecordRepository attendanceRepo;
    private final EmployeeRepository employeeRepo;

    private Label countInLabel;
    private Label countActiveLabel;
    private Label countScannedLabel;
    private VBox currentlyInList;
    private TableView<AttendanceRecord> scansTable;

    public DashboardScreen(AttendanceRecordRepository attendanceRepo,
                           EmployeeRepository employeeRepo) {
        this.attendanceRepo = attendanceRepo;
        this.employeeRepo = employeeRepo;
        setPadding(new Insets(20));
        buildLayout();
        loadData();
    }

    private void buildLayout() {
        Label title = new Label("Dashboard — Today's Attendance");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleBar = new HBox(8, title, spacer, refreshBtn);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        countInLabel      = summaryValue();
        countActiveLabel  = summaryValue();
        countScannedLabel = summaryValue();
        HBox summary = new HBox(16,
                summaryCard("Currently In",      countInLabel),
                summaryCard("Active Employees",   countActiveLabel),
                summaryCard("Scanned Today",      countScannedLabel));
        summary.setPadding(new Insets(12, 0, 12, 0));

        Label inTitle = new Label("Currently In");
        inTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        currentlyInList = new VBox(4);
        VBox inSection = new VBox(6, inTitle, currentlyInList);
        inSection.setPadding(new Insets(0, 0, 12, 0));

        Label scansTitle = new Label("Today's Scans");
        scansTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        scansTable = buildScansTable();
        VBox.setVgrow(scansTable, Priority.ALWAYS);
        VBox scansSection = new VBox(6, scansTitle, scansTable);
        VBox.setVgrow(scansSection, Priority.ALWAYS);

        VBox top = new VBox(0, titleBar, summary, inSection);
        setTop(top);
        setCenter(scansSection);
    }

    private static Label summaryValue() {
        Label lbl = new Label("—");
        lbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        return lbl;
    }

    private static VBox summaryCard(String label, Label valueLabel) {
        Label caption = new Label(label);
        caption.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        VBox card = new VBox(2, valueLabel, caption);
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle("-fx-background-color: #f4f4f5; -fx-padding: 12; -fx-background-radius: 8;");
        card.setMinWidth(140);
        return card;
    }

    @SuppressWarnings("unchecked")
    private TableView<AttendanceRecord> buildScansTable() {
        TableView<AttendanceRecord> table = new TableView<>();
        table.setPlaceholder(new Label("No scans recorded today."));
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

        table.getColumns().addAll(timeCol, codeCol, nameCol, dirCol);
        return table;
    }

    private void loadData() {
        LocalDate today = LocalDate.now();
        List<AttendanceRecord> todayScans = attendanceRepo.findByDateRange(today, today);
        List<Employee> activeEmployees    = employeeRepo.findAllActive();

        // Group by employee id
        Map<Long, List<AttendanceRecord>> byEmployee = todayScans.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        // Odd scan count → currently in (last scan is an ENTRY)
        List<AttendanceRecord> currentlyIn = byEmployee.values().stream()
                .filter(scans -> scans.size() % 2 == 1)
                .map(scans -> scans.get(scans.size() - 1))
                .sorted(Comparator.comparing(AttendanceRecord::getScanDatetime))
                .collect(Collectors.toList());

        countInLabel.setText(String.valueOf(currentlyIn.size()));
        countActiveLabel.setText(String.valueOf(activeEmployees.size()));
        countScannedLabel.setText(String.valueOf(byEmployee.size()));

        currentlyInList.getChildren().clear();
        if (currentlyIn.isEmpty()) {
            currentlyInList.getChildren().add(new Label("No employees currently in."));
        } else {
            for (AttendanceRecord r : currentlyIn) {
                currentlyInList.getChildren().add(new Label(
                        r.getEmployee().getEmployeeCode() + "  " + r.getEmployee().getName()
                        + " — clocked in at " + r.getScanDatetime().format(TIME_FMT)));
            }
        }

        List<AttendanceRecord> reversed = new ArrayList<>(todayScans);
        Collections.reverse(reversed);
        scansTable.setItems(FXCollections.observableArrayList(reversed));
    }
}
