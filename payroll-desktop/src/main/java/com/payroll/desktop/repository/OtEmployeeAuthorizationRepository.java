package com.payroll.desktop.repository;

import com.payroll.core.entity.OtEmployeeAuthorization;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class OtEmployeeAuthorizationRepository {

    private final SessionFactory sessionFactory;

    public OtEmployeeAuthorizationRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public OtEmployeeAuthorization save(OtEmployeeAuthorization auth) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(auth);
            session.getTransaction().commit();
            return auth;
        }
    }

    public OtEmployeeAuthorization update(OtEmployeeAuthorization auth) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            OtEmployeeAuthorization merged = session.merge(auth);
            session.getTransaction().commit();
            return merged;
        }
    }

    public Optional<OtEmployeeAuthorization> findByEmployeeAndDate(Long employeeId, LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM OtEmployeeAuthorization WHERE employeeId = :eid AND date = :date",
                            OtEmployeeAuthorization.class)
                    .setParameter("eid", employeeId)
                    .setParameter("date", date)
                    .uniqueResultOptional();
        }
    }

    public List<OtEmployeeAuthorization> findByDate(LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM OtEmployeeAuthorization WHERE date = :date",
                            OtEmployeeAuthorization.class)
                    .setParameter("date", date)
                    .list();
        }
    }

    public OtEmployeeAuthorization upsert(Long employeeId, LocalDate date,
                                          boolean authorized, String setBy) {
        var existing = findByEmployeeAndDate(employeeId, date);
        if (existing.isPresent()) {
            OtEmployeeAuthorization auth = existing.get();
            auth.setAuthorized(authorized);
            auth.setSetBy(setBy);
            auth.setSetAt(Instant.now());
            return update(auth);
        } else {
            OtEmployeeAuthorization auth = new OtEmployeeAuthorization();
            auth.setEmployeeId(employeeId);
            auth.setDate(date);
            auth.setAuthorized(authorized);
            auth.setSetBy(setBy);
            auth.setSetAt(Instant.now());
            return save(auth);
        }
    }
}
