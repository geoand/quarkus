package io.quarkus.hibernate.orm.runtime;

import java.util.*;
import java.util.concurrent.*;

import io.quarkus.hibernate.orm.*;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

public class DefaultPersistenceUnitMetadata implements PersistenceUnitMetadata {
    private final Set<String> classNames;

    // poor man's implementation of lazy loading: ConcurrentHashMap with only one entry
    private final Integer DUMMY_KEY = 0;
    private final Map<Integer, Set<Class<?>>> resolvedClasses = new ConcurrentHashMap<>();

    DefaultPersistenceUnitMetadata(Set<String> classNames) {
        this.classNames = unmodifiableSet(classNames);
    }

    @Override
    public Set<String> getEntityClassNames() {
        return classNames;
    }

    @Override
    public Set<Class<?>> resolveEntityClasses(ClassLoader classLoader) {
        return resolvedClasses.computeIfAbsent(DUMMY_KEY, ignored -> buildPersistenceUnitMetadata(classNames, classLoader));
    }

    @Override
    public Set<Class<?>> resolveEntityClasses() {
        return this.resolveEntityClasses(Thread.currentThread().getContextClassLoader());
    }

    private Set<Class<?>> buildPersistenceUnitMetadata(Set<String> classNames, ClassLoader classLoader) {
        Set<Class<?>> classes = classNames.stream()
                .map(className -> classForName(className, classLoader))
                .collect(toSet());

        return unmodifiableSet(classes);
    }

    private Class<?> classForName(String className, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Could not load class: " + className + " using current threads ContextClassLoader", e);
        }
    }

}
