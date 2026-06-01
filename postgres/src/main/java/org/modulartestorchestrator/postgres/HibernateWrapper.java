package org.modulartestorchestrator.postgres;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class HibernateWrapper implements AutoCloseable {

    private final SessionFactory sessionFactory;

    public HibernateWrapper(DbConfig config) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url",      config.url())
                .applySetting("hibernate.connection.username", config.username())
                .applySetting("hibernate.connection.password", config.password())
                .applySetting("hibernate.dialect",             "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto",        "validate")
                .applySetting("hibernate.show_sql",            "false")
                .applySetting("hibernate.format_sql",          "false")
                .applySetting("hibernate.use_sql_comments",    "false")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        config.entities().forEach(sources::addAnnotatedClass);

        this.sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    public <T> Optional<T> findById(Class<T> entityClass, Object id) {
        try (var session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.get(entityClass, id));
        }
    }

    public <T> boolean exists(Class<T> entityClass, Object id) {
        return findById(entityClass, id).isPresent();
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> findByFields(T example) {
        Class<T> entityClass = (Class<T>) example.getClass();
        try (var session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);

            cq.where(buildPredicates(example, root, cb).toArray(new Predicate[0]));
            List<T> results = session.createQuery(cq).getResultList();
            if (results.size() > 1) {
                throw new IllegalStateException(
                        "findByFields returned " + results.size() + " results for " +
                        entityClass.getSimpleName() + " — expected at most 1. " +
                        "Narrow your criteria or use countByFields/findById.");
            }
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> long countByFields(T example) {
        Class<T> entityClass = (Class<T>) example.getClass();
        try (var session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(entityClass);

            cq.select(cb.count(root)).where(buildPredicates(example, root, cb).toArray(new Predicate[0]));
            return session.createQuery(cq).getSingleResult();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> List<Predicate> buildPredicates(T example, Root<T> root, CriteriaBuilder cb)
            throws IllegalAccessException {
        List<Predicate> predicates = new ArrayList<>();
        Class<?> current = example.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(example);
                if (value != null && !(value instanceof Collection)) {
                    predicates.add(cb.equal(root.get(field.getName()), value));
                }
            }
            current = current.getSuperclass();
        }
        return predicates;
    }

    public <T> T persist(T entity) {
        try (var session = sessionFactory.openSession()) {
            var tx = session.beginTransaction();
            try {
                session.persist(entity);
                tx.commit();
                return entity;
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    public <T> void delete(Class<T> entityClass, Object id) {
        try (var session = sessionFactory.openSession()) {
            var tx = session.beginTransaction();
            try {
                T entity = session.get(entityClass, id);
                if (entity == null) {
                    throw new IllegalStateException(
                            "delete found no entity of type " + entityClass.getSimpleName() +
                            " with id=" + id);
                }
                session.remove(entity);
                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    public void close() {
        sessionFactory.close();
    }
}
