package com.payroll.desktop.repository;

import com.payroll.core.entity.UserAccount;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;

public class UserAccountRepository {

    private final SessionFactory sessionFactory;

    public UserAccountRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public UserAccount save(UserAccount account) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(account);
            session.getTransaction().commit();
            return account;
        }
    }

    public Optional<UserAccount> findByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM UserAccount WHERE username = :username", UserAccount.class)
                    .setParameter("username", username)
                    .uniqueResultOptional();
        }
    }

    public long countAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("SELECT count(u) FROM UserAccount u", Long.class)
                    .uniqueResult();
        }
    }

    public List<UserAccount> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM UserAccount", UserAccount.class).list();
        }
    }
}
