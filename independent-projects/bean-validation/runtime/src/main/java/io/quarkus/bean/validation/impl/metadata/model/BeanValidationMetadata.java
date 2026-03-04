package io.quarkus.bean.validation.impl.metadata.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level container holding all validation metadata discovered at build time.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record BeanValidationMetadata(Map<String, ConstrainedBeanMetadata> beans,
        Set<String> validatedConstraintDefinitions,
        Set<String> crossParameterValidatorClassNames,
        Map<String, List<String>> interfaceGroupSequences,
        Set<String> convertGroupValidatedBeans,
        Map<String, String> validatorTargetTypes,
        Map<String, List<String>> constraintValidatorMapping,
        Map<String, String> invalidConstraintDefinitions,
        Map<String, String> invalidConstraintDeclarations,
        Map<String, String> convertGroupDuplicateErrors,
        Map<String, String> iterableTypeArguments) {

    public ConstrainedBeanMetadata getBean(String className) {
        return beans.get(className);
    }

    public boolean isEmpty() {
        return beans.isEmpty();
    }
}
