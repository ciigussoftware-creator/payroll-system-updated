package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.payroll.desktop.db.DatabaseManager;
import com.payroll.desktop.repository.AttendanceRecordRepository;
import com.payroll.desktop.repository.EmployeeRepository;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SyncServiceIT {

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

    /** Resets syncedToCloud to false directly — simulates a lost local write. */
    private static void resetSyncedToCloud(DatabaseManager db, Long recordId) {
        try (Session session = db.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                            "UPDATE AttendanceRecord SET syncedToCloud = false WHERE id = :id")
                    .setParameter("id", recordId)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }

    @Test
    void allUnsyncedRecordsGetPushedAndMarkedSynced(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            SyncService service = new SyncService(repo, stub);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY);
            savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 17, 0), ScanType.EXIT);

            SyncRunResult result = service.syncUnsyncedRecords();

            assertThat(result.attempted()).isEqualTo(2);
            assertThat(result.synced()).isEqualTo(2);
            assertThat(result.failed()).isEqualTo(0);
            assertThat(result.skippedOffline()).isFalse();
            assertThat(repo.findUnsynced()).isEmpty();
        }
    }

    @Test
    void offlineSkipsAllRecordsAndSetsSkippedOffline(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            stub.setCloudReachable(false);
            SyncService service = new SyncService(repo, stub);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY);
            savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 17, 0), ScanType.EXIT);

            SyncRunResult result = service.syncUnsyncedRecords();

            assertThat(result.skippedOffline()).isTrue();
            assertThat(result.attempted()).isEqualTo(0);
            assertThat(repo.findUnsynced()).hasSize(2);
        }
    }

    @Test
    void oneFailingRecordDoesNotBlockOthersAndStoresError(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            SyncService service = new SyncService(repo, stub);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            AttendanceRecord r1 = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY);
            AttendanceRecord r2 = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 12, 0), ScanType.EXIT);
            AttendanceRecord r3 = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 13, 0), ScanType.ENTRY);

            stub.setFailForUuid(r2.getSyncUuid());

            SyncRunResult result = service.syncUnsyncedRecords();

            assertThat(result.attempted()).isEqualTo(3);
            assertThat(result.synced()).isEqualTo(2);
            assertThat(result.failed()).isEqualTo(1);

            List<AttendanceRecord> stillUnsynced = repo.findUnsynced();
            assertThat(stillUnsynced).hasSize(1);
            assertThat(stillUnsynced.get(0).getId()).isEqualTo(r2.getId());

            Optional<AttendanceRecord> failed = repo.findById(r2.getId());
            assertThat(failed.get().getSyncAttempts()).isEqualTo(1);
            assertThat(failed.get().getLastSyncError()).contains("forced failure");
            assertThat(failed.get().getLastSyncAttemptAt()).isNotNull();

            // r1 and r3 are synced
            assertThat(repo.findById(r1.getId()).get().isSyncedToCloud()).isTrue();
            assertThat(repo.findById(r3.getId()).get().isSyncedToCloud()).isTrue();
        }
    }

    @Test
    void idempotentRePushMarksRecordSyncedWithoutCreatingDuplicate(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            SyncService service = new SyncService(repo, stub);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            AttendanceRecord r = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY);

            // First push — cloud assigns an ID
            SyncRunResult first = service.syncUnsyncedRecords();
            assertThat(first.synced()).isEqualTo(1);
            Long firstCloudId = repo.findById(r.getId()).get().getCloudRecordId();
            assertThat(firstCloudId).isNotNull();

            // Simulate lost local write (e.g., crash between push and markSynced)
            resetSyncedToCloud(db, r.getId());
            assertThat(repo.findUnsynced()).hasSize(1);

            // Second push — stub sees same uuid, returns alreadyExisted=true
            SyncRunResult second = service.syncUnsyncedRecords();
            assertThat(second.synced()).isEqualTo(1);
            assertThat(repo.findUnsynced()).isEmpty();

            // Same cloudRecordId — no duplicate created
            Long secondCloudId = repo.findById(r.getId()).get().getCloudRecordId();
            assertThat(secondCloudId).isEqualTo(firstCloudId);

            // Stub only accepted one distinct push
            assertThat(stub.getPushedCount()).isEqualTo(1);
        }
    }

    @Test
    void syncUuidIsTheKeyForIdempotencyAndNeverChanges(@TempDir Path tempDir) throws IOException {
        try (DatabaseManager db = new DatabaseManager(tempDir)) {
            EmployeeRepository empRepo = new EmployeeRepository(db.getSessionFactory());
            AttendanceRecordRepository repo = new AttendanceRecordRepository(db.getSessionFactory());
            StubCloudSyncClient stub = new StubCloudSyncClient();
            SyncService service = new SyncService(repo, stub);

            Employee emp = savedEmployee(empRepo, "EMP-001");
            AttendanceRecord r = savedRecord(repo, emp, LocalDateTime.of(2026, 6, 20, 8, 0), ScanType.ENTRY);
            String originalUuid = r.getSyncUuid();

            service.syncUnsyncedRecords();
            assertThat(stub.getPushedCount()).isEqualTo(1);

            resetSyncedToCloud(db, r.getId());

            // Same uuid pushed again → stub count stays at 1 (idempotent)
            service.syncUnsyncedRecords();
            assertThat(stub.getPushedCount()).isEqualTo(1);

            // uuid on the persisted record is unchanged
            assertThat(repo.findById(r.getId()).get().getSyncUuid()).isEqualTo(originalUuid);
        }
    }
}
