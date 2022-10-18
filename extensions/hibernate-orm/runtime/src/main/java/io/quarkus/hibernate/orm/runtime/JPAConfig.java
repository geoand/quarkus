package io.quarkus.hibernate.orm.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.runtime.boot.RuntimePersistenceUnitDescriptor;

@Singleton
public class JPAConfig {

    private static final Logger LOGGER = Logger.getLogger(JPAConfig.class.getName());

    private final Map<String, LazyPersistenceUnit> persistenceUnits = new HashMap<>();
    private final Set<String> deactivatedPersistenceUnitNames = new HashSet<>();

    @Inject
    public JPAConfig(HibernateOrmRuntimeConfig hibernateOrmRuntimeConfig) {
        Map<String, HibernateOrmRuntimeConfigPersistenceUnit> puConfigMap = hibernateOrmRuntimeConfig
                .getAllPersistenceUnitConfigsAsMap();
        for (RuntimePersistenceUnitDescriptor descriptor : PersistenceUnitsHolder.getPersistenceUnitDescriptors()) {
            String puName = descriptor.getName();
            var puConfig = puConfigMap.getOrDefault(descriptor.getConfigurationName(),
                    new HibernateOrmRuntimeConfigPersistenceUnit());
            if (puConfig.active.isPresent() && !puConfig.active.get()) {
                LOGGER.infof("Hibernate ORM persistence unit '%s' was deactivated through configuration properties",
                        puName);
                deactivatedPersistenceUnitNames.add(puName);
            } else {
                persistenceUnits.put(puName, new LazyPersistenceUnit(puName));
            }
        }
    }

    void startAll() {
        //by using a dedicated thread for starting up the PR,
        //we work around https://github.com/quarkusio/quarkus/issues/17304 to some extent
        //as the main thread is now no longer polluted with ThreadLocals by default
        //this is not a complete fix, but will help as long as the test methods
        //don't access the datasource directly, but only over HTTP calls

        // optimize for the common case
        if (persistenceUnits.size() == 1) {
            CompletableFuture<?> future = new CompletableFuture<>();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    completeFuture(persistenceUnits.values().iterator().next(), future);
                }
            }, "JPA Startup Thread").start();
            waitForFuture(future);
        } else {
            List<CompletableFuture<?>> start = new ArrayList<>();
            //start PU's in parallel, for faster startup
            for (Map.Entry<String, LazyPersistenceUnit> i : persistenceUnits.entrySet()) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                start.add(future);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        completeFuture(i.getValue(), future);
                    }
                }, "JPA Startup Thread: " + i.getKey()).start();
            }
            for (CompletableFuture<?> i : start) {
                waitForFuture(i);
            }
        }

    }

    private void completeFuture(LazyPersistenceUnit lazyPersistenceUnit, CompletableFuture<?> future) {
        try {
            lazyPersistenceUnit.get();
            future.complete(null);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private void waitForFuture(CompletableFuture<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public EntityManagerFactory getEntityManagerFactory(String unitName) {
        LazyPersistenceUnit lazyPersistenceUnit = null;
        if (unitName == null) {
            if (persistenceUnits.size() == 1) {
                lazyPersistenceUnit = persistenceUnits.values().iterator().next();
            }
        } else {
            lazyPersistenceUnit = persistenceUnits.get(unitName);
        }

        if (lazyPersistenceUnit == null) {
            if (deactivatedPersistenceUnitNames.contains(unitName)) {
                throw new IllegalStateException(
                        "Cannot retrieve the EntityManagerFactory/SessionFactory for persistence unit "
                                + unitName
                                + ": Hibernate ORM was deactivated through configuration properties");
            }
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Unable to find an EntityManagerFactory for persistence unit '%s'", unitName));
        }

        return lazyPersistenceUnit.get();
    }

    /**
     * Returns the registered, active persistence units.
     *
     * @return Set containing the names of all registered, actives persistence units.
     */
    public Set<String> getPersistenceUnits() {
        return persistenceUnits.keySet();
    }

    /**
     * Returns the name of persistence units that were deactivated through configuration properties.
     *
     * @return Set containing the names of all persistence units that were deactivated through configuration properties.
     */
    public Set<String> getDeactivatedPersistenceUnitNames() {
        return deactivatedPersistenceUnitNames;
    }

    /**
     * Need to shut down all instances of Hibernate ORM before the actual destroy event,
     * as it might need to use the datasources during shutdown.
     *
     * @param event ignored
     */
    void destroy(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        for (LazyPersistenceUnit factory : persistenceUnits.values()) {
            try {
                factory.close();
            } catch (Exception e) {
                LOGGER.warn("Unable to close the EntityManagerFactory: " + factory, e);
            }
        }
    }

    @PreDestroy
    void destroy() {
        persistenceUnits.clear();
    }

    static final class LazyPersistenceUnit {

        private final String name;
        private volatile EntityManagerFactory value;
        private volatile boolean closed = false;

        LazyPersistenceUnit(String name) {
            this.name = name;
        }

        EntityManagerFactory get() {
            if (value == null) {
                synchronized (this) {
                    if (closed) {
                        throw new IllegalStateException("Persistence unit is closed");
                    }
                    if (value == null) {
                        value = Persistence.createEntityManagerFactory(name);
                    }
                }
            }
            return value;
        }

        public synchronized void close() {
            closed = true;
            EntityManagerFactory emf = this.value;
            this.value = null;
            if (emf != null) {
                emf.close();
            }
        }
    }

}
