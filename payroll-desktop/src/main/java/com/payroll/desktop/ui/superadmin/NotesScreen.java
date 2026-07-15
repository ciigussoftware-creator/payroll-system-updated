package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeNote;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.admin.EmployeeNoteService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only notes viewer, filtered by month and (optionally) employee. Notes are added from the Dashboard. */
public class NotesScreen extends BorderPane {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EmployeeRepository employeeRepo;
    private final EmployeeNoteService noteService;

    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final ComboBox<MonthItem> monthBox = new ComboBox<>();
    private final ComboBox<Employee> employeeCombo = new ComboBox<>();
    private final ObservableList<EmployeeNote> noteItems = FXCollections.observableArrayList();
    private final TableView<EmployeeNote> noteTable = new TableView<>(noteItems);
    private final Label statusLabel = new Label();

    private Map<Long, Employee> employeesById = Map.of();

    public NotesScreen(EmployeeRepository employeeRepo, EmployeeNoteService noteService) {
        this.employeeRepo = employeeRepo;
        this.noteService = noteService;

        setTop(buildFilterBar());
        setCenter(buildTable());
        setPadding(new Insets(16));

        loadEmployees();
    }

    // ── filter bar ────────────────────────────────────────────────────────────────

    private HBox buildFilterBar() {
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear + 2; y++) yearBox.getItems().add(y);
        yearBox.setValue(currentYear);
        yearBox.setPrefWidth(90);

        for (Month m : Month.values()) monthBox.getItems().add(new MonthItem(m));
        monthBox.setValue(new MonthItem(LocalDate.now().getMonth()));
        monthBox.setPrefWidth(140);

        employeeCombo.setMinWidth(220);
        employeeCombo.setCellFactory(lv -> employeeCell());
        employeeCombo.setButtonCell(employeeCell());

        Button loadBtn = new Button("Load");
        loadBtn.setDefaultButton(true);
        loadBtn.setOnAction(e -> loadNotes());

        HBox bar = new HBox(12,
                new Label("Year:"), yearBox,
                new Label("Month:"), monthBox,
                new Label("Employee:"), employeeCombo,
                loadBtn, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 12, 0));
        return bar;
    }

    private ListCell<Employee> employeeCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(e == null ? "All employees" : e.getEmployeeCode() + " — " + e.getName());
                }
            }
        };
    }

    // ── notes table ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<EmployeeNote> buildTable() {
        TableColumn<EmployeeNote, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getNoteDate().toString()));
        dateCol.setPrefWidth(100);

        TableColumn<EmployeeNote, String> employeeCol = new TableColumn<>("Employee");
        employeeCol.setCellValueFactory(cd -> {
            Employee e = employeesById.get(cd.getValue().getEmployeeId());
            String label = e == null
                    ? "Employee #" + cd.getValue().getEmployeeId()
                    : e.getEmployeeCode() + " — " + e.getName();
            return new SimpleStringProperty(label);
        });
        employeeCol.setPrefWidth(200);

        TableColumn<EmployeeNote, String> textCol = new TableColumn<>("Note");
        textCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNoteText()));
        textCol.setPrefWidth(320);

        TableColumn<EmployeeNote, String> byCol = new TableColumn<>("Created By");
        byCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCreatedBy()));
        byCol.setPrefWidth(110);

        TableColumn<EmployeeNote, String> atCol = new TableColumn<>("Created At");
        atCol.setCellValueFactory(cd -> {
            String ts = cd.getValue().getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(DT_FMT);
            return new SimpleStringProperty(ts);
        });
        atCol.setPrefWidth(140);

        noteTable.getColumns().setAll(dateCol, employeeCol, textCol, byCol, atCol);
        noteTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        noteTable.setPlaceholder(new Label("Select a month and click Load."));
        VBox.setVgrow(noteTable, Priority.ALWAYS);
        return noteTable;
    }

    // ── data ops ──────────────────────────────────────────────────────────────────

    private void loadEmployees() {
        List<Employee> all = employeeRepo.findAll();
        employeesById = all.stream().collect(Collectors.toMap(Employee::getId, e -> e));

        employeeCombo.getItems().add(null);
        employeeCombo.getItems().addAll(all);
        employeeCombo.setValue(null);
    }

    private void loadNotes() {
        Integer year = yearBox.getValue();
        MonthItem month = monthBox.getValue();
        if (year == null || month == null) {
            statusLabel.setText("Select a year and month.");
            return;
        }
        String periodMonth = String.format("%04d-%02d", year, month.number());
        Employee emp = employeeCombo.getValue();

        List<EmployeeNote> notes = emp == null
                ? noteService.findByMonth(periodMonth)
                : noteService.findByEmployeeAndMonth(emp.getId(), periodMonth);

        noteItems.setAll(notes);
        statusLabel.setText(notes.size() + " note(s) for "
                + (emp == null ? "all employees" : emp.getName()) + " in " + periodMonth);
    }

    // ── helper record ──────────────────────────────────────────────────────────

    private record MonthItem(Month month) {
        int number() { return month.getValue(); }
        @Override public String toString() {
            return month.getDisplayName(TextStyle.FULL, Locale.getDefault());
        }
    }
}
