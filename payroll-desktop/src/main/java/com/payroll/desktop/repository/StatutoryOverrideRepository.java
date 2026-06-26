package com.payroll.desktop.repository;

import com.payroll.core.entity.StatutoryOverride;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class StatutoryOverrideRepository {

    private final SessionFactory sessionFactory;

    public StatutoryOverrideRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public StatutoryOverride save(StatutoryOverride o) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(o);
            session.getTransaction().commit();
            return o;
        }
    }

    public StatutoryOverride update(StatutoryOverride o) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            StatutoryOverride merged = session.merge(o);
            session.getTransaction().commit();
            return merged;
        }
    }

    public Optional<StatutoryOverride> findByEmployeeAndMonth(Long employeeId, String periodMonth) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM StatutoryOverride WHERE employeeId = :eid AND periodMonth = :pm",
                            StatutoryOverride.class)
                    .setParameter("eid", employeeId)
                    .setParameter("pm", periodMonth)
                    .uniqueResultOptional();
        }
    }

    public List<StatutoryOverride> findByMonth(String periodMonth) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM StatutoryOverride WHERE periodMonth = :pm",
                            StatutoryOverride.class)
                    .setParameter("pm", periodMonth)
                    .list();
        }
    }

    /** Creates or updates an override. Throws if reason is blank. */
    public StatutoryOverride upsert(Long employeeId, String periodMonth,
                                    BigDecimal days, String reason, String username) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Override reason is required");
        }
        var existing = findByEmployeeAndMonth(employeeId, periodMonth);
        if (existing.isPresent()) {
            StatutoryOverride o = existing.get();
            o.setOverriddenDaysWorked(days);
            o.setReason(reason);
            o.setCreatedAt(Instant.now());
            o.setCreatedBy(username);
            return update(o);
        } else {
            StatutoryOverride o = new StatutoryOverride();
            o.setEmployeeId(employeeId);
            o.setPeriodMonth(periodMonth);
            o.setOverriddenDaysWorked(days);
            o.setReason(reason);
            o.setCreatedAt(Instant.now());
            o.setCreatedBy(username);
            return save(o);
        }
    }
}
