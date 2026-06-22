package com.payroll.desktop.sync;

public record SyncRunResult(int attempted, int synced, int failed, boolean skippedOffline) {}
