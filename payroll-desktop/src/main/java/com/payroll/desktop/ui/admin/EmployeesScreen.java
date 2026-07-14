package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.desktop.repository.EmployeeRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

public class EmployeesScreen extends BorderPane {

    private final EmployeeRepository repository;
    private final ObservableList<Employee> allData = FXCollections.observableArrayList();
    private final FilteredList<Employee> view      = new FilteredList<>(allData);

    public EmployeesScreen(EmployeeRepository repository) {
        this.repository = repository;
        setPadding(new Insets(12));
        setTop(buildToolbar());
        setCenter(buildTable());
        refreshData();
    }

    // ── toolbar ────────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Button addBtn = new Button("+ Add Employee");
        addBtn.setOnAction(e -> showAddDialog());

        CheckBox activeOnly = new CheckBox("Active only");
        activeOnly.setOnAction(e ->
            view.setPredicate(activeOnly.isSelected() ? Employee::isActive : emp -> true));

        HBox toolbar = new HBox(12, addBtn, activeOnly);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));
        return toolbar;
    }

    // ── table ──────────────────────────────────────────────────────────────────

    private TableView<Employee> buildTable() {
        var table = new TableView<>(view);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No employees found."));

        TableColumn<Employee, String> codeCol = col("Employee Code", "employeeCode", 130);
        TableColumn<Employee, String> nameCol = col("Name", "name", 190);
        TableColumn<Employee, String> rfidCol = col("RFID Card ID", "rfidCardId", 140);

        TableColumn<Employee, EmployeeCategory> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        catCol.setPrefWidth(100);

        TableColumn<Employee, Boolean> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        statusCol.setPrefWidth(80);
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                setText(empty || active == null ? null : (active ? "Active" : "Inactive"));
            }
        });

        table.getColumns().addAll(codeCol, nameCol, rfidCol, catCol, statusCol,
                                   buildActionsColumn());

        // Dim inactive rows visually
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Employee item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(item != null && !item.isActive() ? "-fx-opacity: 0.5;" : "");
            }
        });

        return table;
    }

    private TableColumn<Employee, Void> buildActionsColumn() {
        TableColumn<Employee, Void> col = new TableColumn<>("Actions");
        col.setPrefWidth(165);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button editBtn       = new Button("Edit");
            private final Button deactivateBtn = new Button("Deactivate");
            private final HBox   box           = new HBox(4, editBtn, deactivateBtn);

            {
                editBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    showEditDialog(emp);
                });
                deactivateBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    confirmDeactivate(emp);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                Employee emp = getTableView().getItems().get(getIndex());
                deactivateBtn.setDisable(!emp.isActive());
                setGraphic(box);
            }
        });
        return col;
    }

    // ── actions ────────────────────────────────────────────────────────────────

    private void showAddDialog() {
        new EmployeeDialog(getScene().getWindow(), repository, null)
                .showAndGet()
                .ifPresent(saved -> refreshData());
    }

    private void showEditDialog(Employee emp) {
        new EmployeeDialog(getScene().getWindow(), repository, emp)
                .showAndGet()
                .ifPresent(updated -> refreshData());
    }

    private void confirmDeactivate(Employee emp) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(getScene().getWindow());
        alert.setTitle("Deactivate Employee");
        alert.setHeaderText("Deactivate " + emp.getName() + "?");
        alert.setContentText("Their attendance records will be kept. This cannot be undone here.");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                repository.deactivate(emp.getId());
                refreshData();
            }
        });
    }

    private void refreshData() {
        allData.setAll(repository.findAll());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static <T> TableColumn<Employee, T> col(String title, String property, double width) {
        var c = new TableColumn<Employee, T>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        c.setPrefWidth(width);
        return c;
    }
}
