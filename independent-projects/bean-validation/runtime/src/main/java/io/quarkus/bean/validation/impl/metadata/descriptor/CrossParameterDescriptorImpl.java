package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.CrossParameterDescriptor;

class CrossParameterDescriptorImpl implements CrossParameterDescriptor {

    private final Set<ConstraintDescriptor<?>> constraintDescriptors;

    CrossParameterDescriptorImpl(Set<ConstraintDescriptor<?>> constraintDescriptors) {
        this.constraintDescriptors = constraintDescriptors != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(constraintDescriptors))
                : Collections.emptySet();
    }

    @Override
    public boolean hasConstraints() {
        return !constraintDescriptors.isEmpty();
    }

    @Override
    public Class<?> getElementClass() {
        return Object[].class;
    }

    @Override
    public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
        return constraintDescriptors;
    }

    @Override
    public ConstraintFinder findConstraints() {
        return new ConstraintFinderImpl(constraintDescriptors);
    }

    public <T> T unwrap(Class<T> type) {
        throw new ValidationException("Cannot unwrap to " + type);
    }
}
