package io.quarkus.bean.validation.impl.metadata.model;

import java.util.List;
import java.util.Set;

/**
 * Represents a validated bean class and all of its constrained elements.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record ConstrainedBeanMetadata(String className, List<ConstraintMetadata> classConstraints,
        List<ConstrainedFieldMetadata> fields, List<ConstrainedMethodMetadata> methods,
        List<ConstrainedMethodMetadata> constructors, List<String> groupSequence,
        List<String> typeHierarchy, Set<String> allPropertyNames) {
}
