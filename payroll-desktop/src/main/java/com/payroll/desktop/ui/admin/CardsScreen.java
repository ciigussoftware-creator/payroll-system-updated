package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.Employee;
import com.payroll.desktop.repository.EmployeeRepository;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

public class CardsScreen extends BorderPane {

    public CardsScreen(EmployeeRepository repository) {
        setPadding(new Insets(12));

        Label note = new Label("Cards are assigned when adding or editing an employee. This view is read-only.");
        note.setStyle("-fx-text-fill: #666666; -fx-font-style: italic;");

        var table = new TableView<Employee>();
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No RFID cards assigned yet."));

        TableColumn<Employee, String> rfidCol = col("RFID Card ID",   "rfidCardId",   160);
        TableColumn<Employee, String> codeCol = col("Employee Code",  "employeeCode", 130);
        TableColumn<Employee, String> nameCol = col("Employee Name",  "name",         200);

        TableColumn<Employee, Boolean> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        statusCol.setPrefWidth(80);
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                setText(empty || active == null ? null : (active ? "Active" : "Inactive"));
            }
        });

        table.getColumns().addAll(rfidCol, codeCol, nameCol, statusCol);

        var rows = repository.findAll().stream()
                .filter(e -> e.getRfidCardId() != null && !e.getRfidCardId().isBlank())
                .toList();
        table.setItems(FXCollections.observableArrayList(rows));

        VBox header = new VBox(6, note);
        header.setPadding(new Insets(0, 0, 10, 0));
        setTop(header);
        setCenter(table);
    }

    private static <T> TableColumn<Employee, T> col(String title, String property, double width) {
        var c = new TableColumn<Employee, T>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        c.setPrefWidth(width);
        return c;
    }
}
