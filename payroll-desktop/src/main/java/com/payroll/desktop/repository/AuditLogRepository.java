package com.payroll.desktop.repository;

import com.payroll.core.entity.AuditLogEntry;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.util.List;

public class AuditLogRepository {

    private final SessionFactory sessionFactory;

    public AuditLogRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public AuditLogEntry save(AuditLogEntry entry) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(entry);
            session.getTransaction().commit();
            return entry;
        }
    }

    public List<AuditLogEntry> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM AuditLogEntry ORDER BY entryDatetime DESC",
                            AuditLogEntry.class)
                    .list();
        }
    }

    public List<AuditLogEntry> findByDateRange(Instant from, Instant to) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM AuditLogEntry WHERE entryDatetime >= :from AND entryDatetime <= :to " +
                            "ORDER BY entryDatetime DESC",
                            AuditLogEntry.class)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .list();
        }
    }
}
