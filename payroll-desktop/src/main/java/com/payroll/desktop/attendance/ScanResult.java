package com.payroll.desktop.attendance;

import com.payroll.core.entity.ScanType;

import java.time.LocalDateTime;

public record ScanResult(
        Outcome outcome,
        String employeeName,
        String employeeCode,
        ScanType scanType,
        LocalDateTime time,
        String cardNumber
) {

    public enum Outcome {
        ACCEPTED,
        IGNORED_TOO_SOON,
        REJECTED_UNKNOWN_CARD
    }

    public static ScanResult accepted(String name, String code, ScanType scanType, LocalDateTime time) {
        return new ScanResult(Outcome.ACCEPTED, name, code, scanType, time, null);
    }

    public static ScanResult ignoredTooSoon() {
        return new ScanResult(Outcome.IGNORED_TOO_SOON, null, null, null, null, null);
    }

    public static ScanResult rejectedUnknownCard(String cardNumber) {
        return new ScanResult(Outcome.REJECTED_UNKNOWN_CARD, null, null, null, null, cardNumber);
    }
}
