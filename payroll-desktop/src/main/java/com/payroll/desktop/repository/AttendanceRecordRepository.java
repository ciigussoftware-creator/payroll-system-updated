package com.payroll.desktop.repository;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.ScanType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AttendanceRecordRepository {

    private final SessionFactory sessionFactory;

    public AttendanceRecordRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public AttendanceRecord save(AttendanceRecord r) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            // getReference avoids a detached-entity error when the employee comes from a prior session
            r.setEmployee(session.getReference(Employee.class, r.getEmployee().getId()));
            session.persist(r);
            session.getTransaction().commit();
            return r;
        }
    }

    public Optional<AttendanceRecord> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT r FROM AttendanceRecord r JOIN FETCH r.employee WHERE r.id = :id",
                            AttendanceRecord.class)
                    .setParameter("id", id)
                    .uniqueResultOptional();
        }
    }

    public List<AttendanceRecord> findByEmployeeAndDate(Employee e, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT r FROM AttendanceRecord r JOIN FETCH r.employee " +
                            "WHERE r.employee.id = :empId " +
                            "AND r.scanDatetime >= :dayStart AND r.scanDatetime < :dayEnd " +
                            "ORDER BY r.scanDatetime",
                            AttendanceRecord.class)
                    .setParameter("empId", e.getId())
                    .setParameter("dayStart", dayStart)
                    .setParameter("dayEnd", dayEnd)
                    .list();
        }
    }

    public List<AttendanceRecord> findUnsynced() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT r FROM AttendanceRecord r JOIN FETCH r.employee " +
                            "WHERE r.syncedToCloud = false ORDER BY r.scanDatetime ASC",
                            AttendanceRecord.class)
                    .list();
        }
    }

    public void markSynced(Long id, Long cloudRecordId) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                            "UPDATE AttendanceRecord SET syncedToCloud = true, cloudRecordId = :cloudId " +
                            "WHERE id = :id")
                    .setParameter("cloudId", cloudRecordId)
                    .setParameter("id", id)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }

    public void incrementSyncAttempt(Long id, String errorMessage) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                            "UPDATE AttendanceRecord SET syncAttempts = syncAttempts + 1, " +
                            "lastSyncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
                    .setParameter("error", errorMessage)
                    .setParameter("attemptAt", Instant.now())
                    .setParameter("id", id)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }

    public Optional<AttendanceRecord> findBySyncUuid(String uuid) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT r FROM AttendanceRecord r JOIN FETCH r.employee " +
                            "WHERE r.syncUuid = :uuid",
                            AttendanceRecord.class)
                    .setParameter("uuid", uuid)
                    .uniqueResultOptional();
        }
    }

    /**
     * Applies a timestamp correction in-place using targeted HQL to avoid detached-entity issues.
     * If originalToPreserve is non-null (first correction), also writes originalScanDatetime.
     * Always resets syncedToCloud=false so the corrected record is re-queued for sync.
     *
     * TODO: cloud conflict — if the original record was already synced, the cloud's add-only
     * policy means the corrected version cannot overwrite it. A conflict-resolution strategy
     * (e.g. a cloud "correction" endpoint, or flagging the record for manual review) is needed
     * before cloud sync is enabled for corrections.
     */
    public void applyCorrection(Long id, LocalDateTime newDatetime, ScanType newScanType,
                                LocalDateTime originalToPreserve, String correctionNote) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            if (originalToPreserve != null) {
                session.createMutationQuery(
                                "UPDATE AttendanceRecord SET scanDatetime = :newDt, scanType = :newType, " +
                                "originalScanDatetime = :origDt, correctionNote = :note, syncedToCloud = false " +
                                "WHERE id = :id")
                        .setParameter("newDt", newDatetime)
                        .setParameter("newType", newScanType)
                        .setParameter("origDt", originalToPreserve)
                        .setParameter("note", correctionNote)
                        .setParameter("id", id)
                        .executeUpdate();
            } else {
                session.createMutationQuery(
                                "UPDATE AttendanceRecord SET scanDatetime = :newDt, scanType = :newType, " +
                                "correctionNote = :note, syncedToCloud = false WHERE id = :id")
                        .setParameter("newDt", newDatetime)
                        .setParameter("newType", newScanType)
                        .setParameter("note", correctionNote)
                        .setParameter("id", id)
                        .executeUpdate();
            }
            session.getTransaction().commit();
        }
    }

    public List<AttendanceRecord> findByDateRange(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT r FROM AttendanceRecord r JOIN FETCH r.employee " +
                            "WHERE r.scanDatetime >= :fromDt AND r.scanDatetime < :toDt " +
                            "ORDER BY r.scanDatetime",
                            AttendanceRecord.class)
                    .setParameter("fromDt", fromDt)
                    .setParameter("toDt", toDt)
                    .list();
        }
    }
}
