package io.quarkus.bean.validation.impl.metadata.model;

import java.util.List;
import java.util.Map;

/**
 * Represents constraints on a container element type argument,
 * e.g. {@code List<@NotNull String>} or {@code Map<@NotBlank String, @Valid Address>}.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record ContainerElementConstraint(int typeArgumentIndex, List<ConstraintMetadata> constraints, boolean cascading,
        String containerClassName, Map<String, String> groupConversions,
        List<ContainerElementConstraint> nestedContainerElements) {
}
