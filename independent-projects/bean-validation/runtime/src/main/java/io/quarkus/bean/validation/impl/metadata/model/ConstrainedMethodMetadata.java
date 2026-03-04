package io.quarkus.bean.validation.impl.metadata.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a constrained method or constructor of a validated bean.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record ConstrainedMethodMetadata(String name, String declaringClassName, String returnTypeName,
        List<String> parameterTypeNames, List<ConstrainedParameterMetadata> parameters,
        List<ConstraintMetadata> returnValueConstraints, boolean returnValueCascading,
        List<ConstraintMetadata> crossParameterConstraints,
        List<ContainerElementConstraint> returnValueContainerElementConstraints,
        Map<String, String> returnValueGroupConversions, boolean constructor,
        boolean getter) {
}
