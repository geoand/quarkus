package io.quarkus.bean.validation.impl.metadata.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a constrained field of a validated bean.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record ConstrainedFieldMetadata(String name, String declaringClassName, String fieldTypeName,
        List<ConstraintMetadata> constraints, boolean cascading,
        List<ContainerElementConstraint> containerElementConstraints,
        Map<String, String> groupConversions) {
}
