package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;

/**
 * Shared base for descriptor implementations that support cascading, group conversions,
 * and container element types (PropertyDescriptor, ReturnValueDescriptor, ParameterDescriptor,
 * ContainerElementTypeDescriptor).
 */
abstract class AbstractCascadableDescriptorImpl implements ElementDescriptor {

    private final Class<?> elementClass;
    private final Set<ConstraintDescriptor<?>> constraintDescriptors;
    private final boolean isCascaded;
    private final Set<GroupConversionDescriptor> groupConversions;
    private final Set<ContainerElementTypeDescriptor> containerElementTypes;

    AbstractCascadableDescriptorImpl(Class<?> elementClass,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            boolean isCascaded,
            Set<GroupConversionDescriptor> groupConversions,
            Set<ContainerElementTypeDescriptor> containerElementTypes) {
        this.elementClass = elementClass;
        this.constraintDescriptors = constraintDescriptors != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(constraintDescriptors))
                : Collections.emptySet();
        this.isCascaded = isCascaded;
        this.groupConversions = groupConversions != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(groupConversions))
                : Collections.emptySet();
        this.containerElementTypes = containerElementTypes != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(containerElementTypes))
                : Collections.emptySet();
    }

    public boolean isCascaded() {
        return isCascaded;
    }

    public Set<GroupConversionDescriptor> getGroupConversions() {
        return groupConversions;
    }

    public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
        return containerElementTypes;
    }

    @Override
    public boolean hasConstraints() {
        return !constraintDescriptors.isEmpty();
    }

    @Override
    public Class<?> getElementClass() {
        return elementClass;
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
