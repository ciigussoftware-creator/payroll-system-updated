package com.payroll.desktop.statutory;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/** Writes a statutory export to .xlsx. Contains ONLY statutory columns — no OT, no allowances. */
public class StatutoryExcelExporter {

    static final String[] HEADERS = {
        "Employee Code", "Name", "Available Days", "Computed Days",
        "Effective Days", "Gross", "EPF 8%", "EPF 12%", "ETF 3%", "Admin Balance"
    };

    public void export(List<StatutoryRow> rows, String periodMonth, Path destination)
            throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             OutputStream out = new FileOutputStream(destination.toFile())) {

            Sheet sheet = wb.createSheet("Statutory " + periodMonth);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIdx = 1;
            for (StatutoryRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.employeeCode());
                row.createCell(1).setCellValue(r.name());
                row.createCell(2).setCellValue(r.availableWorkingDays());
                setCellDecimal(row, 3, r.computedDaysWorked());
                setCellDecimal(row, 4, r.effectiveDaysWorked());
                setCellDecimal(row, 5, r.gross());
                setCellDecimal(row, 6, r.epfEmployee());
                setCellDecimal(row, 7, r.epfEmployer());
                setCellDecimal(row, 8, r.etf());
                setCellDecimal(row, 9, r.adminBalance());
            }
            wb.write(out);
        }
    }

    private static void setCellDecimal(Row row, int col, BigDecimal value) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }
}
