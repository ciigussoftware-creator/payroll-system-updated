package com.payroll.web.calculation;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Renders the same per-employee data as {@link PayrollCalculationController} (via
 * {@link PayrollCalculationService}, reused unchanged) into a confidential .xlsx workbook —
 * the durable, auditable record of a payroll run, since calculation results are never
 * persisted.
 */
@Service
public class PayrollExcelExportService {

    private static final String[] HEADERS = {
            "Employee Code", "Name", "Available Working Days", "Days Worked", "OT Hours",
            "OT Pay (Rs)", "Gross (Rs)", "EPF Employee 8% (Rs)", "EPF Employer 12% (Rs)",
            "ETF 3% (Rs)", "Allowance (Rs)", "Salary Advance (Rs)", "Festival Advance (Rs)",
            "Take-Home (Rs)"
    };

    private static final int COL_EMPLOYEE_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_AVAILABLE_WORKING_DAYS = 2;
    private static final int COL_DAYS_WORKED = 3;
    private static final int COL_OT_HOURS = 4;
    private static final int COL_OT_PAY = 5;
    private static final int COL_GROSS = 6;
    private static final int COL_EPF_EMPLOYEE = 7;
    private static final int COL_EPF_EMPLOYER = 8;
    private static final int COL_ETF = 9;
    private static final int COL_ALLOWANCE = 10;
    private static final int COL_SALARY_ADVANCE = 11;
    private static final int COL_FESTIVAL_ADVANCE = 12;
    private static final int COL_TAKE_HOME = 13;

    private static final int[] MONEY_COLUMNS = {
            COL_OT_PAY, COL_GROSS, COL_EPF_EMPLOYEE, COL_EPF_EMPLOYER, COL_ETF,
            COL_ALLOWANCE, COL_SALARY_ADVANCE, COL_FESTIVAL_ADVANCE, COL_TAKE_HOME
    };

    private static final int MAX_SHEET_NAME_LENGTH = 31;

    public byte[] export(String companyName, String periodMonth, List<PayrollCalculationResponse> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName(companyName, periodMonth));

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            CellStyle textStyle = wb.createCellStyle();

            short decimalFormat = wb.createDataFormat().getFormat("0.00");
            CellStyle decimalStyle = wb.createCellStyle();
            decimalStyle.setDataFormat(decimalFormat);

            CellStyle boldDecimalStyle = wb.createCellStyle();
            boldDecimalStyle.setDataFormat(decimalFormat);
            boldDecimalStyle.setFont(boldFont);

            CellStyle notSetTextStyle = wb.createCellStyle();
            notSetTextStyle.cloneStyleFrom(textStyle);
            notSetTextStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            notSetTextStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle notSetDecimalStyle = wb.createCellStyle();
            notSetDecimalStyle.cloneStyleFrom(decimalStyle);
            notSetDecimalStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            notSetDecimalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            writeHeader(sheet, headerStyle);
            sheet.createFreezePane(0, 1);

            BigDecimal[] totals = new BigDecimal[MONEY_COLUMNS.length];
            Arrays.fill(totals, BigDecimal.ZERO);

            int rowIdx = 1;
            for (PayrollCalculationResponse r : rows) {
                Row row = sheet.createRow(rowIdx++);
                if (r.workingDaysNotSet()) {
                    writeNotSetRow(row, r, notSetTextStyle, notSetDecimalStyle);
                } else {
                    writeRow(row, r, textStyle, decimalStyle, totals);
                }
            }

            writeTotalsRow(sheet, rowIdx, headerStyle, boldDecimalStyle, totals);

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String fileName(String companyName, String periodMonth) {
        return displayName(companyName, periodMonth) + ".xlsx";
    }

    private String displayName(String companyName, String periodMonth) {
        return companyName + " Payroll " + periodMonth;
    }

    private String sheetName(String companyName, String periodMonth) {
        String name = displayName(companyName, periodMonth);
        return name.length() > MAX_SHEET_NAME_LENGTH ? name.substring(0, MAX_SHEET_NAME_LENGTH) : name;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeRow(Row row, PayrollCalculationResponse r, CellStyle textStyle,
                           CellStyle decimalStyle, BigDecimal[] totals) {
        setText(row, COL_EMPLOYEE_CODE, r.employeeCode(), textStyle);
        setText(row, COL_NAME, r.name(), textStyle);
        setNumeric(row, COL_AVAILABLE_WORKING_DAYS, r.availableWorkingDays(), textStyle);
        setDecimal(row, COL_DAYS_WORKED, r.daysWorked(), decimalStyle);
        setDecimal(row, COL_OT_HOURS, r.otHours(), decimalStyle);
        setDecimal(row, COL_OT_PAY, r.otPay(), decimalStyle);
        setDecimal(row, COL_GROSS, r.gross(), decimalStyle);
        setDecimal(row, COL_EPF_EMPLOYEE, r.epfEmployee(), decimalStyle);
        setDecimal(row, COL_EPF_EMPLOYER, r.epfEmployer(), decimalStyle);
        setDecimal(row, COL_ETF, r.etf(), decimalStyle);
        setDecimal(row, COL_ALLOWANCE, r.allowance(), decimalStyle);
        setDecimal(row, COL_SALARY_ADVANCE, r.salaryAdvance(), decimalStyle);
        setDecimal(row, COL_FESTIVAL_ADVANCE, r.festivalAdvance(), decimalStyle);
        setDecimal(row, COL_TAKE_HOME, r.takeHome(), decimalStyle);

        BigDecimal[] values = {
                r.otPay(), r.gross(), r.epfEmployee(), r.epfEmployer(), r.etf(),
                r.allowance(), r.salaryAdvance(), r.festivalAdvance(), r.takeHome()
        };
        for (int i = 0; i < values.length; i++) {
            totals[i] = totals[i].add(values[i]);
        }
    }

    private void writeNotSetRow(Row row, PayrollCalculationResponse r, CellStyle textStyle, CellStyle decimalStyle) {
        setText(row, COL_EMPLOYEE_CODE, r.employeeCode(), textStyle);
        setText(row, COL_NAME, r.name(), textStyle);
        setNumeric(row, COL_AVAILABLE_WORKING_DAYS, r.availableWorkingDays(), textStyle);
        setDecimal(row, COL_DAYS_WORKED, r.daysWorked(), decimalStyle);
        setDecimal(row, COL_OT_HOURS, r.otHours(), decimalStyle);
        for (int col : MONEY_COLUMNS) {
            setText(row, col, "N/A", textStyle);
        }
    }

    private void writeTotalsRow(Sheet sheet, int rowIdx, CellStyle labelStyle,
                                 CellStyle boldDecimalStyle, BigDecimal[] totals) {
        Row row = sheet.createRow(rowIdx);
        var labelCell = row.createCell(COL_EMPLOYEE_CODE);
        labelCell.setCellValue("Total");
        labelCell.setCellStyle(labelStyle);

        for (int i = 0; i < MONEY_COLUMNS.length; i++) {
            var cell = row.createCell(MONEY_COLUMNS[i]);
            cell.setCellValue(totals[i].doubleValue());
            cell.setCellStyle(boldDecimalStyle);
        }
    }

    private void setText(Row row, int col, String value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setNumeric(Row row, int col, double value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setDecimal(Row row, int col, BigDecimal value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }
}
