package com.payroll.desktop.sync;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An {@link com.payroll.core.entity.OtEmployeeAuthorization} with its employeeId already
 * resolved to the cloud-facing employeeCode, so {@link CloudSyncClient} implementations
 * never need repository access of their own.
 */
public record OtAuthorizationRecord(
        String employeeCode,
        LocalDate authDate,
        boolean authorized,
        String setBy,
        Instant setAt
) {}
