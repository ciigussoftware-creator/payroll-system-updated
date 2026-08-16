package com.payroll.web.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record NoteRequest(
        @NotNull(message = "companyId is required") Long companyId,
        @NotBlank(message = "employeeCode is required") String employeeCode,
        @NotNull(message = "noteDate is required") LocalDate noteDate,
        @NotBlank(message = "text is required") String text
) {}
