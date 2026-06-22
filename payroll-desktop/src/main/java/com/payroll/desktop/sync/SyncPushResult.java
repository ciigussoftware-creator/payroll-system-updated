package com.payroll.desktop.sync;

public record SyncPushResult(Long cloudRecordId, boolean alreadyExisted) {}
