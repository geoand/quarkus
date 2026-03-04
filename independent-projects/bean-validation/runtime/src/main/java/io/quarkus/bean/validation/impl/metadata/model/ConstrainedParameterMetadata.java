package io.quarkus.bean.validation.impl.metadata.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a constrained parameter of a method or constructor.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record ConstrainedParameterMetadata(String name, int index, String typeName,
        List<ConstraintMetadata> constraints, boolean cascading,
        List<ContainerElementConstraint> containerElementConstraints,
        Map<String, String> groupConversions) {
}
