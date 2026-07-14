package com.payroll.desktop.ui.superadmin;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeNote;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.ui.auth.UserSession;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NotesScreen extends BorderPane {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserSession session;
    private final EmployeeRepository employeeRepo;
    private final EmployeeNoteService noteService;

    private final ComboBox<Employee> employeeCombo = new ComboBox<>();
    private final ObservableList<EmployeeNote> noteItems = FXCollections.observableArrayList();
    private final TableView<EmployeeNote> noteTable = new TableView<>(noteItems);
    private final DatePicker noteDatePicker = new DatePicker(LocalDate.now());
    private final TextArea noteTextArea = new TextArea();
    private final Label statusLabel = new Label();

    public NotesScreen(UserSession session,
                       EmployeeRepository employeeRepo,
                       EmployeeNoteService noteService) {
        this.session = session;
        this.employeeRepo = employeeRepo;
        this.noteService = noteService;

        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().addAll(buildTopPanel(), buildAddPanel());
        split.setDividerPositions(0.65);

        setTop(buildFilterBar());
        setCenter(split);
        setPadding(new Insets(16));

        loadEmployees();
    }

    // ── filter bar ────────────────────────────────────────────────────────────────

    private HBox buildFilterBar() {
        employeeCombo.setPromptText("Select employee…");
        employeeCombo.setMinWidth(220);
        employeeCombo.setCellFactory(lv -> employeeCell());
        employeeCombo.setButtonCell(employeeCell());

        Button loadBtn = new Button("Load Notes");
        loadBtn.setOnAction(e -> loadNotes());

        HBox bar = new HBox(12, new Label("Employee:"), employeeCombo, loadBtn, statusLabel);
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

    // ── notes table (top half) ────────────────────────────────────────────────────

    private VBox buildTopPanel() {
        TableColumn<EmployeeNote, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(
                        cd.getValue().getNoteDate().toString()));
        dateCol.setPrefWidth(100);

        TableColumn<EmployeeNote, String> textCol = new TableColumn<>("Note");
        textCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getNoteText()));
        textCol.setPrefWidth(380);

        TableColumn<EmployeeNote, String> byCol = new TableColumn<>("Created By");
        byCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getCreatedBy()));
        byCol.setPrefWidth(110);

        TableColumn<EmployeeNote, String> atCol = new TableColumn<>("Created At");
        atCol.setCellValueFactory(cd -> {
            String ts = cd.getValue().getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(DT_FMT);
            return new javafx.beans.property.SimpleStringProperty(ts);
        });
        atCol.setPrefWidth(140);

        noteTable.getColumns().setAll(dateCol, textCol, byCol, atCol);
        noteTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        noteTable.setPlaceholder(new Label("Select an employee and click Load Notes."));
        VBox.setVgrow(noteTable, Priority.ALWAYS);

        VBox panel = new VBox(new Label("Existing Notes"), noteTable);
        panel.setSpacing(6);
        panel.setPadding(new Insets(0, 0, 8, 0));
        VBox.setVgrow(noteTable, Priority.ALWAYS);
        return panel;
    }

    // ── add note form (bottom half) ───────────────────────────────────────────────

    private VBox buildAddPanel() {
        noteTextArea.setPromptText("Enter note text…");
        noteTextArea.setPrefHeight(80);
        noteTextArea.setWrapText(true);

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: red;");

        Button saveBtn = new Button("Save Note");
        saveBtn.setOnAction(e -> saveNote(errLabel));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(8);
        form.add(new Label("Note Date:"), 0, 0);
        form.add(noteDatePicker, 1, 0);
        form.add(new Label("Note Text:"), 0, 1);
        form.add(noteTextArea, 1, 1);
        GridPane.setHgrow(noteTextArea, Priority.ALWAYS);

        VBox panel = new VBox(8, new Label("Add Note"), form, errLabel, saveBtn, statusLabel);
        panel.setPadding(new Insets(8, 0, 0, 0));
        return panel;
    }

    // ── data ops ──────────────────────────────────────────────────────────────────

    private void loadEmployees() {
        employeeCombo.getItems().setAll(employeeRepo.findAll());
    }

    private void loadNotes() {
        Employee emp = employeeCombo.getValue();
        if (emp == null) {
            statusLabel.setText("Select an employee first.");
            return;
        }
        List<EmployeeNote> notes = noteService.findByEmployee(emp.getId());
        noteItems.setAll(notes);
        statusLabel.setText(notes.size() + " note(s) for " + emp.getName());
    }

    private void saveNote(Label errLabel) {
        Employee emp = employeeCombo.getValue();
        if (emp == null) { errLabel.setText("Select an employee first."); return; }
        String text = noteTextArea.getText().trim();
        if (text.isEmpty()) { errLabel.setText("Note text cannot be empty."); return; }
        LocalDate noteDate = noteDatePicker.getValue();
        if (noteDate == null) { errLabel.setText("Select a note date."); return; }

        try {
            noteService.addNote(emp.getId(), noteDate, text, session.getUsername());
            noteTextArea.clear();
            errLabel.setText("");
            statusLabel.setText("Note saved.");
            loadNotes();
        } catch (Exception ex) {
            errLabel.setText("Error: " + ex.getMessage());
        }
    }
}
