package com.payroll.desktop.repository;

import com.payroll.core.entity.EmployeeNote;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class EmployeeNoteRepository {

    private final SessionFactory sessionFactory;

    public EmployeeNoteRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EmployeeNote save(EmployeeNote note) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(note);
            session.getTransaction().commit();
            return note;
        }
    }

    public List<EmployeeNote> findByEmployeeAndDate(Long employeeId, LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM EmployeeNote WHERE employeeId = :empId AND noteDate = :date " +
                            "ORDER BY createdAt ASC",
                            EmployeeNote.class)
                    .setParameter("empId", employeeId)
                    .setParameter("date", date)
                    .list();
        }
    }

    public List<EmployeeNote> findByEmployee(Long employeeId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM EmployeeNote WHERE employeeId = :empId ORDER BY createdAt DESC",
                            EmployeeNote.class)
                    .setParameter("empId", employeeId)
                    .list();
        }
    }

    public List<EmployeeNote> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM EmployeeNote ORDER BY createdAt DESC",
                            EmployeeNote.class)
                    .list();
        }
    }

    /** All notes across all employees whose noteDate falls within the given "YYYY-MM" month. */
    public List<EmployeeNote> findByMonth(String periodMonth) {
        YearMonth ym = YearMonth.parse(periodMonth);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM EmployeeNote WHERE noteDate >= :start AND noteDate <= :end " +
                            "ORDER BY noteDate ASC, createdAt ASC",
                            EmployeeNote.class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .list();
        }
    }

    /** One employee's notes whose noteDate falls within the given "YYYY-MM" month. */
    public List<EmployeeNote> findByEmployeeAndMonth(Long employeeId, String periodMonth) {
        YearMonth ym = YearMonth.parse(periodMonth);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM EmployeeNote WHERE employeeId = :empId " +
                            "AND noteDate >= :start AND noteDate <= :end " +
                            "ORDER BY noteDate ASC, createdAt ASC",
                            EmployeeNote.class)
                    .setParameter("empId", employeeId)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .list();
        }
    }
}
