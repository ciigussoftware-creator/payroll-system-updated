package com.payroll.desktop.repository;

import com.payroll.core.entity.WorkingDaysConfig;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class WorkingDaysConfigRepository {

    private final SessionFactory sessionFactory;

    public WorkingDaysConfigRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public WorkingDaysConfig save(WorkingDaysConfig config) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(config);
            session.getTransaction().commit();
            return config;
        }
    }

    public WorkingDaysConfig update(WorkingDaysConfig config) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            WorkingDaysConfig merged = session.merge(config);
            session.getTransaction().commit();
            return merged;
        }
    }

    public Optional<WorkingDaysConfig> findByPeriodMonth(String periodMonth) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM WorkingDaysConfig WHERE periodMonth = :pm", WorkingDaysConfig.class)
                    .setParameter("pm", periodMonth)
                    .uniqueResultOptional();
        }
    }

    /** Returns all configs ordered by periodMonth descending (lexicographic == chronological for YYYY-MM). */
    public List<WorkingDaysConfig> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM WorkingDaysConfig ORDER BY periodMonth DESC", WorkingDaysConfig.class)
                    .list();
        }
    }

    /** Creates a new config if none exists for periodMonth; otherwise updates days, updatedAt, updatedBy in place. */
    public WorkingDaysConfig upsert(String periodMonth, int days, String username) {
        var existing = findByPeriodMonth(periodMonth);
        if (existing.isPresent()) {
            WorkingDaysConfig config = existing.get();
            config.setAvailableWorkingDays(days);
            config.setUpdatedAt(Instant.now());
            config.setUpdatedBy(username);
            return update(config);
        } else {
            WorkingDaysConfig config = new WorkingDaysConfig();
            config.setPeriodMonth(periodMonth);
            config.setAvailableWorkingDays(days);
            config.setUpdatedAt(Instant.now());
            config.setUpdatedBy(username);
            return save(config);
        }
    }
}
