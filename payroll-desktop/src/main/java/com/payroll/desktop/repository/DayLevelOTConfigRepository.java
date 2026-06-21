package com.payroll.desktop.repository;

import com.payroll.core.entity.DayLevelOTConfig;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class DayLevelOTConfigRepository {

    private final SessionFactory sessionFactory;

    public DayLevelOTConfigRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public DayLevelOTConfig save(DayLevelOTConfig config) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(config);
            session.getTransaction().commit();
            return config;
        }
    }

    public DayLevelOTConfig update(DayLevelOTConfig config) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            DayLevelOTConfig merged = session.merge(config);
            session.getTransaction().commit();
            return merged;
        }
    }

    public Optional<DayLevelOTConfig> findByDate(LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM DayLevelOTConfig WHERE configDate = :date", DayLevelOTConfig.class)
                    .setParameter("date", date)
                    .uniqueResultOptional();
        }
    }

    public List<DayLevelOTConfig> findByDateRange(LocalDate from, LocalDate to) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM DayLevelOTConfig WHERE configDate >= :from AND configDate <= :to " +
                            "ORDER BY configDate",
                            DayLevelOTConfig.class)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .list();
        }
    }

    public void deleteByDate(LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                            "DELETE FROM DayLevelOTConfig WHERE configDate = :date")
                    .setParameter("date", date)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }
}
