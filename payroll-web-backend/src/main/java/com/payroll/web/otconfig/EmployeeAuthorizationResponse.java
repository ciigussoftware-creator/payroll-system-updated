package com.payroll.web.otconfig;

import com.payroll.core.entity.CloudOtEmployeeAuthorization;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeAuthorizationResponse(
        String employeeCode,
        LocalDate authDate,
        boolean authorized,
        Instant setAt,
        String setBy
) {
    static EmployeeAuthorizationResponse from(CloudOtEmployeeAuthorization auth) {
        return new EmployeeAuthorizationResponse(
                auth.getEmployeeCode(), auth.getAuthDate(), auth.isAuthorized(), auth.getSetAt(), auth.getSetBy());
    }
}
