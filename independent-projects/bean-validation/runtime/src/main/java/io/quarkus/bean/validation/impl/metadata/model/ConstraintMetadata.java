package io.quarkus.bean.validation.impl.metadata.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single constraint annotation instance.
 * <p>
 * This is a simple data class with no Jandex or Jakarta Validation API dependencies.
 */
public record ConstraintMetadata(String annotationClassName, Map<String, Object> attributes, List<String> groups,
        List<String> payload, String validatorClassName, boolean reportAsSingleViolation,
        List<ConstraintMetadata> composingConstraints,
        Map<String, Map<String, String>> overridesAttributes, String defaultMessageTemplate,
        List<String> allValidatorClassNames, Map<String, String> attributeTypes) {

    /**
     * Returns composing constraints with @OverridesAttribute mappings pre-applied.
     * Used by the metadata API to build correct ConstraintDescriptor trees.
     */
    public List<ConstraintMetadata> getResolvedComposingConstraints() {
        if (composingConstraints == null || composingConstraints.isEmpty() || overridesAttributes.isEmpty()) {
            return composingConstraints;
        }
        List<ConstraintMetadata> resolved = new ArrayList<>();
        Map<String, Integer> typeIndexCounters = new HashMap<>();
        for (ConstraintMetadata composing : composingConstraints) {
            String composingType = composing.annotationClassName();
            int typeIndex = typeIndexCounters.getOrDefault(composingType, 0);
            typeIndexCounters.put(composingType, typeIndex + 1);

            // Look up override mappings for this composing constraint
            Map<String, String> mappings = overridesAttributes.get(composingType + ":" + typeIndex);
            if (mappings == null) {
                mappings = overridesAttributes.get(composingType + ":-1");
            }

            if (mappings != null) {
                Map<String, Object> overriddenAttrs = new LinkedHashMap<>(composing.attributes());
                for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                    String targetName = mapping.getKey();
                    String sourceName = mapping.getValue();
                    Object value = this.attributes.get(sourceName);
                    if (value != null) {
                        overriddenAttrs.put(targetName, value);
                    }
                }
                resolved.add(new ConstraintMetadata(
                        composing.annotationClassName(),
                        overriddenAttrs,
                        composing.groups(),
                        composing.payload(),
                        composing.validatorClassName(),
                        composing.reportAsSingleViolation(),
                        composing.composingConstraints(),
                        composing.overridesAttributes(),
                        composing.defaultMessageTemplate(),
                        composing.allValidatorClassNames(),
                        composing.attributeTypes()));
            } else {
                resolved.add(composing);
            }
        }
        return resolved;
    }

    public ConstraintMetadata withInheritedGroupsPayloadAndAttributes(
            List<String> inheritedGroups,
            List<String> inheritedPayload,
            Map<String, Object> attributeOverrides) {
        Map<String, Object> mergedAttributes = this.attributes;
        if (attributeOverrides != null && !attributeOverrides.isEmpty()) {
            mergedAttributes = new LinkedHashMap<>(this.attributes);
            mergedAttributes.putAll(attributeOverrides);
        }
        return new ConstraintMetadata(
                this.annotationClassName,
                mergedAttributes,
                inheritedGroups,
                inheritedPayload,
                this.validatorClassName,
                this.reportAsSingleViolation,
                this.composingConstraints,
                this.overridesAttributes,
                this.defaultMessageTemplate,
                this.allValidatorClassNames,
                this.attributeTypes);
    }
}
