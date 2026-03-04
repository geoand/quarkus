package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.groups.Default;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import jakarta.validation.metadata.Scope;

/**
 * Package-private implementation of {@link ElementDescriptor.ConstraintFinder} that provides
 * filtering of constraint descriptors by groups and constraint type (scope).
 */
class ConstraintFinderImpl implements ElementDescriptor.ConstraintFinder {

    private final Set<ConstraintDescriptor<?>> allConstraints;
    private Set<Class<?>> groupFilter;

    ConstraintFinderImpl(Set<ConstraintDescriptor<?>> constraints) {
        this.allConstraints = constraints != null ? constraints : Collections.emptySet();
    }

    @Override
    public ElementDescriptor.ConstraintFinder unorderedAndMatchingGroups(Class<?>... groups) {
        this.groupFilter = new LinkedHashSet<>(Arrays.asList(groups));
        return this;
    }

    @Override
    public ElementDescriptor.ConstraintFinder lookingAt(Scope scope) {
        // Scope filtering is a no-op: all constraints in our model are local.
        return this;
    }

    @Override
    public ElementDescriptor.ConstraintFinder declaredOn(java.lang.annotation.ElementType... types) {
        // ElementType filtering would require knowing whether the constraint was placed
        // on a FIELD, METHOD, etc. -- we accept all for now.
        return this;
    }

    @Override
    public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
        Set<ConstraintDescriptor<?>> result = new LinkedHashSet<>(allConstraints);

        if (groupFilter != null) {
            result.removeIf(cd -> {
                Set<Class<?>> constraintGroups = cd.getGroups();
                if (constraintGroups.isEmpty()) {
                    // Constraints with no explicit groups belong to Default
                    return !groupFilter.contains(Default.class);
                }
                for (Class<?> constraintGroup : constraintGroups) {
                    for (Class<?> filterGroup : groupFilter) {
                        // Per BV spec 5.4: group inheritance. If filterGroup extends
                        // constraintGroup, then constraintGroup's constraints are included.
                        if (constraintGroup.isAssignableFrom(filterGroup)) {
                            return false;
                        }
                    }
                }
                return true;
            });
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public boolean hasConstraints() {
        return !getConstraintDescriptors().isEmpty();
    }
}
