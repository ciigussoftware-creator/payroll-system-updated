package com.payroll.web.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TimestampCorrectionRequest(
        @NotNull(message = "companyId is required") Long companyId,
        @NotNull(message = "newScanDatetime is required") LocalDateTime newScanDatetime,
        @NotBlank(message = "reason is required") String reason
) {}
