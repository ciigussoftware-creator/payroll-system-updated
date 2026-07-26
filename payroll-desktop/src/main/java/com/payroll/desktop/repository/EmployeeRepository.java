package com.payroll.desktop.repository;

import com.payroll.core.entity.Employee;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;

public class EmployeeRepository {

    private final SessionFactory sessionFactory;

    public EmployeeRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Employee save(Employee e) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(e);
            session.getTransaction().commit();
            return e;
        }
    }

    public Employee update(Employee e) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            Employee merged = session.merge(e);
            session.getTransaction().commit();
            return merged;
        }
    }

    public Optional<Employee> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.get(Employee.class, id));
        }
    }

    public Optional<Employee> findByEmployeeCode(String code) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Employee WHERE employeeCode = :code", Employee.class)
                    .setParameter("code", code)
                    .uniqueResultOptional();
        }
    }

    public Optional<Employee> findByRfidCardId(String cardId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Employee WHERE rfidCardId = :cardId", Employee.class)
                    .setParameter("cardId", cardId)
                    .uniqueResultOptional();
        }
    }

    public List<Employee> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Employee", Employee.class).list();
        }
    }

    public List<Employee> findAllActive() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Employee WHERE isActive = true", Employee.class)
                    .list();
        }
    }

    public void deactivate(Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                            "UPDATE Employee SET isActive = false WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }

    public void reactivate(Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                            "UPDATE Employee SET isActive = true WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }

    /**
     * Permanently deletes an employee and cascades to every dependent row
     * (attendance records, statutory overrides, notes, OT authorizations)
     * so no orphaned records remain.
     */
    public void deleteById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.createMutationQuery("DELETE FROM AttendanceRecord WHERE employee.id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.createMutationQuery("DELETE FROM StatutoryOverride WHERE employeeId = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.createMutationQuery("DELETE FROM EmployeeNote WHERE employeeId = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.createMutationQuery("DELETE FROM OtEmployeeAuthorization WHERE employeeId = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.createMutationQuery("DELETE FROM Employee WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            session.getTransaction().commit();
        }
    }
}
