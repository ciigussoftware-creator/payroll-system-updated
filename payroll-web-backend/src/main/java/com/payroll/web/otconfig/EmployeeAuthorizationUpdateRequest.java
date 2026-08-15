package com.payroll.web.otconfig;

public record EmployeeAuthorizationUpdateRequest(
        boolean authorized,
        String reason
) {}
