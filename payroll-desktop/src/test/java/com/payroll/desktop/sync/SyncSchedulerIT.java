package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.DayLevelOTConfigRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import com.payroll.desktop.repository.OtEmployeeAuthorizationRepository;
import com.payroll.desktop.repository.WorkingDaysConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.CronTrigger;
import org.quartz.TriggerKey;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SyncSchedulerIT {

    private static Employee savedEmployee(EmployeeRepository empRepo, String code) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setName("Worker " + code);
        e.setRfidCardId("RFID-" + code);
        e.setCategory(EmployeeCategory.STANDARD);
        e.setGrossDailySalary(new BigDecimal("1200.00"));
        return empRepo.save(e);
    }

    private static AttendanceRecord savedRecord(AttendanceRecordRepository repo,
                                                Employee emp, LocalDateTime dt, ScanType type) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(emp);
        r.setScanDatetime(dt);
        r.setScanType(type);
        return repo.save(r);
    }

    @Test
    void triggerNowSyncsUnsyncedRecords(@TempDir Path tempDir) throws Exception {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            DayLevelOTConfigRepository otConfigRepo = new DayLevelOTConfigRepository(db.getSessionFactory());
            OtEmployeeAuthorizationRepository otAuthRepo = new OtEmployeeAuthorizationRepository(db.getSessionFactory());
            WorkingDaysConfigRepository workingDaysRepo = new WorkingDaysConfigRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            SyncService service = new SyncService(empRepo, workingDaysRepo, otConfigRepo, otAuthRepo, repo, stub);
            SyncScheduler scheduler = new SyncScheduler(service);

            scheduler.start();
            try {
                Employee emp = savedEmployee(empRepo, "EMP-S01");
                AttendanceRecord r1 = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 22, 8, 0), ScanType.ENTRY);
                AttendanceRecord r2 = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 22, 17, 0), ScanType.EXIT);

                SyncRunResult result = scheduler.triggerNow();

                assertThat(result.synced()).isEqualTo(2);
                assertThat(result.skippedOffline()).isFalse();
                assertThat(repo.findUnsynced()).isEmpty();
                assertThat(stub.getCallLog()).containsExactly(
                        "employees:1",
                        "workingDays:0",
                        "otConfigs:0",
                        "otAuths:0",
                        "attendance:" + r1.getSyncUuid(),
                        "attendance:" + r2.getSyncUuid());
            } finally {
                scheduler.shutdown();
            }
        }
    }

    @Test
    void triggerNowSkipsAttendanceWhenEmployeePushFails(@TempDir Path tempDir) throws Exception {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            DayLevelOTConfigRepository otConfigRepo = new DayLevelOTConfigRepository(db.getSessionFactory());
            OtEmployeeAuthorizationRepository otAuthRepo = new OtEmployeeAuthorizationRepository(db.getSessionFactory());
            WorkingDaysConfigRepository workingDaysRepo = new WorkingDaysConfigRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            stub.setFailEmployeePush(true);
            SyncService service = new SyncService(empRepo, workingDaysRepo, otConfigRepo, otAuthRepo, repo, stub);
            SyncScheduler scheduler = new SyncScheduler(service);

            scheduler.start();
            try {
                Employee emp = savedEmployee(empRepo, "EMP-S03");
                savedRecord(repo, emp, LocalDateTime.of(2026, 6, 22, 8, 0), ScanType.ENTRY);

                SyncRunResult result = scheduler.triggerNow();

                assertThat(result.attendanceSkippedDueToEmployeeFailure()).isTrue();
                assertThat(result.attempted()).isEqualTo(0);
                assertThat(repo.findUnsynced()).hasSize(1);
                assertThat(stub.getCallLog()).containsExactly("employees:1");
            } finally {
                scheduler.shutdown();
            }
        }
    }

    @Test
    void triggerNowWhileOfflineKeepsRecordsUnsyncedAndDoesNotCrash(@TempDir Path tempDir) throws Exception {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            DayLevelOTConfigRepository otConfigRepo = new DayLevelOTConfigRepository(db.getSessionFactory());
            OtEmployeeAuthorizationRepository otAuthRepo = new OtEmployeeAuthorizationRepository(db.getSessionFactory());
            WorkingDaysConfigRepository workingDaysRepo = new WorkingDaysConfigRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            stub.setCloudReachable(false);
            SyncService service = new SyncService(empRepo, workingDaysRepo, otConfigRepo, otAuthRepo, repo, stub);
            SyncScheduler scheduler = new SyncScheduler(service);

            scheduler.start();
            try {
                Employee emp = savedEmployee(empRepo, "EMP-S02");
                savedRecord(repo, emp, LocalDateTime.of(2026, 6, 22, 9, 0), ScanType.ENTRY);

                SyncRunResult result = scheduler.triggerNow();

                assertThat(result.skippedOffline()).isTrue();
                assertThat(result.attempted()).isEqualTo(0);
                assertThat(repo.findUnsynced()).hasSize(1);
            } finally {
                scheduler.shutdown();
            }
        }
    }

    @Test
    void schedulerStartsAndShutsDownCleanly() throws Exception {
        SyncScheduler scheduler = new SyncScheduler(
                new SyncService(null, null, null, null, null, new StubCloudSyncClient()));
        scheduler.start();
        scheduler.shutdown();
    }

    @Test
    void dailyTriggerIsRegisteredWithExpectedCronExpression() throws Exception {
        SyncScheduler scheduler = new SyncScheduler(
                new SyncService(null, null, null, null, null, new StubCloudSyncClient()));
        scheduler.start();
        try {
            TriggerKey key = TriggerKey.triggerKey(SyncScheduler.TRIGGER_NAME, SyncScheduler.GROUP);
            CronTrigger trigger = (CronTrigger) scheduler.getQuartzScheduler().getTrigger(key);

            assertThat(trigger).isNotNull();
            assertThat(trigger.getCronExpression()).isEqualTo(SyncScheduler.DAILY_SYNC_CRON);
        } finally {
            scheduler.shutdown();
        }
    }
}
