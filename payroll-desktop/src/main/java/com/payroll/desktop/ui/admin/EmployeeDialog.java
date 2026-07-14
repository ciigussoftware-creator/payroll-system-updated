package com.payroll.desktop.ui.admin;

import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.desktop.repository.EmployeeRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.Optional;

/** Add / Edit employee dialog. Package-private — opened only by EmployeesScreen. */
class EmployeeDialog extends Stage {

    private Employee result = null;

    EmployeeDialog(Window owner, EmployeeRepository repository, Employee toEdit) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle(toEdit == null ? "Add Employee" : "Edit Employee");
        setResizable(false);

        TextField codeField   = field(toEdit == null ? "" : toEdit.getEmployeeCode());
        TextField nameField   = field(toEdit == null ? "" : toEdit.getName());
        TextField rfidField   = field(toEdit == null ? "" : nvl(toEdit.getRfidCardId()));
        ComboBox<EmployeeCategory> catBox = new ComboBox<>();
        catBox.getItems().addAll(EmployeeCategory.values());
        catBox.setValue(toEdit == null ? EmployeeCategory.STANDARD : toEdit.getCategory());
        catBox.setMaxWidth(Double.MAX_VALUE);
        TextField salaryField = field(toEdit == null ? "" : toEdit.getGrossDailySalary().toPlainString());
        TextField epfEmpField = field(toEdit == null ? "0.08" : toEdit.getEpfEmployeeRate().toPlainString());
        TextField epfErField  = field(toEdit == null ? "0.12" : toEdit.getEpfEmployerRate().toPlainString());
        TextField etfField    = field(toEdit == null ? "0.03" : toEdit.getEtfRate().toPlainString());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(20, 20, 8, 20));

        int r = 0;
        addRow(form, r++, "Employee Code *",      codeField);
        addRow(form, r++, "Name *",               nameField);
        addRow(form, r++, "RFID Card ID *",        rfidField);
        addRow(form, r++, "Category *",           catBox);
        addRow(form, r++, "Gross Daily Salary *", salaryField);
        addRow(form, r++, "EPF Employee Rate",    epfEmpField);
        addRow(form, r++, "EPF Employer Rate",    epfErField);
        addRow(form, r,   "ETF Rate",             etfField);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #cc0000;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(360);

        Button saveBtn   = new Button("Save");
        saveBtn.setDefaultButton(true);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> close());

        saveBtn.setOnAction(e -> {
            String code   = codeField.getText();
            String name   = nameField.getText();
            String rfid   = rfidField.getText();
            EmployeeCategory cat = catBox.getValue();
            String salary = salaryField.getText();
            String epfEmp = epfEmpField.getText();
            String epfEr  = epfErField.getText();
            String etf    = etfField.getText();

            var fieldOk = EmployeeFormValidator.validateFields(code, name, rfid, cat, salary, epfEmp, epfEr, etf);
            if (!fieldOk.valid()) { errorLabel.setText(fieldOk.error()); return; }

            Long excludeId = toEdit == null ? null : toEdit.getId();
            var uniqueOk = new EmployeeFormValidator(repository).checkUniqueness(code, rfid, excludeId);
            if (!uniqueOk.valid()) { errorLabel.setText(uniqueOk.error()); return; }

            if (toEdit == null) {
                Employee emp = new Employee();
                applyFields(emp, code, name, rfid, cat, salary, epfEmp, epfEr, etf);
                result = repository.save(emp);
            } else {
                applyFields(toEdit, code, name, rfid, cat, salary, epfEmp, epfEr, etf);
                result = repository.update(toEdit);
            }
            close();
        });

        HBox buttons = new HBox(8, saveBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(8, form, errorLabel, buttons);
        root.setPadding(new Insets(0, 20, 20, 20));

        setScene(new Scene(root, 420, 470));
    }

    Optional<Employee> showAndGet() {
        showAndWait();
        return Optional.ofNullable(result);
    }

    private static TextField field(String value) {
        var tf = new TextField(value);
        tf.setPrefWidth(220);
        return tf;
    }

    private static void addRow(GridPane grid, int row, String labelText, Control control) {
        Label lbl = new Label(labelText);
        lbl.setMinWidth(155);
        grid.add(lbl, 0, row);
        grid.add(control, 1, row);
    }

    private static void applyFields(Employee emp, String code, String name, String rfid,
                                     EmployeeCategory cat, String salary,
                                     String epfEmp, String epfEr, String etf) {
        emp.setEmployeeCode(code.trim());
        emp.setName(name.trim());
        emp.setRfidCardId(rfid.trim());
        emp.setCategory(cat);
        emp.setGrossDailySalary(new BigDecimal(salary.trim()));
        emp.setEpfEmployeeRate(new BigDecimal(epfEmp.trim()));
        emp.setEpfEmployerRate(new BigDecimal(epfEr.trim()));
        emp.setEtfRate(new BigDecimal(etf.trim()));
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
