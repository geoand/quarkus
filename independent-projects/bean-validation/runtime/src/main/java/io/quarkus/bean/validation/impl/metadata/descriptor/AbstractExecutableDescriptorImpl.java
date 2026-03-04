package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.CrossParameterDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import jakarta.validation.metadata.ParameterDescriptor;
import jakarta.validation.metadata.ReturnValueDescriptor;

abstract class AbstractExecutableDescriptorImpl implements ElementDescriptor {

    private final String name;
    private final Class<?> elementClass;
    private final Set<ConstraintDescriptor<?>> constraintDescriptors;
    private final List<ParameterDescriptor> parameterDescriptors;
    private final ReturnValueDescriptor returnValueDescriptor;
    private final CrossParameterDescriptor crossParameterDescriptor;

    AbstractExecutableDescriptorImpl(String name,
            Class<?> elementClass,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            List<ParameterDescriptor> parameterDescriptors,
            ReturnValueDescriptor returnValueDescriptor,
            CrossParameterDescriptor crossParameterDescriptor) {
        this.name = name;
        this.elementClass = elementClass;
        this.constraintDescriptors = constraintDescriptors != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(constraintDescriptors))
                : Collections.emptySet();
        this.parameterDescriptors = parameterDescriptors != null
                ? List.copyOf(parameterDescriptors)
                : Collections.emptyList();
        this.returnValueDescriptor = returnValueDescriptor;
        this.crossParameterDescriptor = crossParameterDescriptor;
    }

    public String getName() {
        return name;
    }

    public List<ParameterDescriptor> getParameterDescriptors() {
        return parameterDescriptors;
    }

    public ReturnValueDescriptor getReturnValueDescriptor() {
        return returnValueDescriptor;
    }

    public CrossParameterDescriptor getCrossParameterDescriptor() {
        return crossParameterDescriptor;
    }

    public boolean hasConstrainedParameters() {
        if (crossParameterDescriptor != null && crossParameterDescriptor.hasConstraints()) {
            return true;
        }
        for (ParameterDescriptor param : parameterDescriptors) {
            if (param.hasConstraints() || param.isCascaded()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasConstrainedReturnValue() {
        if (returnValueDescriptor == null) {
            return false;
        }
        return returnValueDescriptor.hasConstraints()
                || returnValueDescriptor.isCascaded()
                || !returnValueDescriptor.getConstrainedContainerElementTypes().isEmpty();
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
