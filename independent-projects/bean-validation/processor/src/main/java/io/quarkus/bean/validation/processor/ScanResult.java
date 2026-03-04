package io.quarkus.bean.validation.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

/**
 * Holds the results of scanning a Jandex index for Bean Validation constraint annotations.
 * <p>
 * This is a mutable accumulator used during the scanning phase. Once scanning is complete
 * the data is converted to immutable runtime metadata by {@link MetadataBuilder}.
 */
class ScanResult {

    private final Map<DotName, List<ConstrainedField>> constrainedFields = new LinkedHashMap<>();
    private final Map<DotName, List<ConstrainedMethod>> constrainedMethods = new LinkedHashMap<>();
    private final Map<DotName, List<ConstrainedMethod>> constrainedConstructors = new LinkedHashMap<>();
    private final Map<DotName, List<ScannedConstraint>> classLevelConstraints = new LinkedHashMap<>();
    private final Map<DotName, List<String>> groupSequences = new LinkedHashMap<>();
    private final Set<DotName> customConstraintAnnotations = new LinkedHashSet<>();
    private final Set<String> validatedConstraintDefinitions = new LinkedHashSet<>();
    private final Set<String> crossParameterValidatorClassNames = new LinkedHashSet<>();
    private final Map<String, List<String>> interfaceGroupSequences = new LinkedHashMap<>();
    private final Set<String> convertGroupDuplicateBeans = new LinkedHashSet<>();
    private final Map<String, String> validatorTargetTypes = new LinkedHashMap<>();
    private final Map<DotName, List<String>> typeHierarchies = new LinkedHashMap<>();
    private final Map<DotName, Set<String>> allPropertyNames = new LinkedHashMap<>();
    private final Map<String, List<String>> constraintValidatorMapping = new LinkedHashMap<>();
    private final Map<String, String> invalidConstraintDefinitions = new LinkedHashMap<>();
    private final Map<String, String> invalidConstraintDeclarations = new LinkedHashMap<>();
    private final Map<String, String> convertGroupDuplicateErrors = new LinkedHashMap<>();
    private final Map<String, String> iterableTypeArguments = new LinkedHashMap<>();

    // ---------------------------------------------------------------------------
    // Inner data classes
    // ---------------------------------------------------------------------------

    public static class ConstrainedField {

        private final String fieldName;
        private final Type fieldType;
        private final DotName declaringClass;
        private final List<ScannedConstraint> constraints;
        private boolean cascading;
        private final List<ContainerElementConstraintInfo> containerElementConstraints;
        private final Map<DotName, DotName> groupConversions;

        public ConstrainedField(String fieldName, Type fieldType, DotName declaringClass,
                List<ScannedConstraint> constraints, boolean cascading,
                List<ContainerElementConstraintInfo> containerElementConstraints,
                Map<DotName, DotName> groupConversions) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.declaringClass = declaringClass;
            this.constraints = constraints != null ? constraints : new ArrayList<>();
            this.cascading = cascading;
            this.containerElementConstraints = containerElementConstraints != null
                    ? containerElementConstraints
                    : new ArrayList<>();
            this.groupConversions = groupConversions != null ? groupConversions : new LinkedHashMap<>();
        }

        public String getFieldName() {
            return fieldName;
        }

        public Type getFieldType() {
            return fieldType;
        }

        public DotName getDeclaringClass() {
            return declaringClass;
        }

        public List<ScannedConstraint> getConstraints() {
            return constraints;
        }

        public boolean isCascading() {
            return cascading;
        }

        public void setCascading(boolean cascading) {
            this.cascading = cascading;
        }

        public List<ContainerElementConstraintInfo> getContainerElementConstraints() {
            return containerElementConstraints;
        }

        public Map<DotName, DotName> getGroupConversions() {
            return groupConversions;
        }
    }

    public static class ConstrainedMethod {

        private final String methodName;
        private final DotName declaringClass;
        private final List<ConstrainedParameter> parameters;
        private final List<ScannedConstraint> returnValueConstraints;
        private boolean returnValueCascading;
        private final List<ScannedConstraint> crossParameterConstraints;
        private final List<ContainerElementConstraintInfo> returnValueContainerElementConstraints;
        private final Map<DotName, DotName> returnValueGroupConversions;
        private final boolean constructor;
        private final boolean getter;
        private final List<Type> parameterTypes;
        private final Type returnType;

        public ConstrainedMethod(String methodName, DotName declaringClass,
                List<ConstrainedParameter> parameters,
                List<ScannedConstraint> returnValueConstraints,
                boolean returnValueCascading,
                List<ScannedConstraint> crossParameterConstraints,
                List<ContainerElementConstraintInfo> returnValueContainerElementConstraints,
                Map<DotName, DotName> returnValueGroupConversions,
                boolean constructor, boolean getter, List<Type> parameterTypes,
                Type returnType) {
            this.methodName = methodName;
            this.declaringClass = declaringClass;
            this.parameters = parameters != null ? parameters : new ArrayList<>();
            this.returnValueConstraints = returnValueConstraints != null ? returnValueConstraints : new ArrayList<>();
            this.returnValueCascading = returnValueCascading;
            this.crossParameterConstraints = crossParameterConstraints != null
                    ? crossParameterConstraints
                    : new ArrayList<>();
            this.returnValueContainerElementConstraints = returnValueContainerElementConstraints != null
                    ? returnValueContainerElementConstraints
                    : new ArrayList<>();
            this.returnValueGroupConversions = returnValueGroupConversions != null
                    ? returnValueGroupConversions
                    : new LinkedHashMap<>();
            this.constructor = constructor;
            this.getter = getter;
            this.parameterTypes = parameterTypes != null ? parameterTypes : Collections.emptyList();
            this.returnType = returnType;
        }

        public String getMethodName() {
            return methodName;
        }

        public DotName getDeclaringClass() {
            return declaringClass;
        }

        public List<ConstrainedParameter> getParameters() {
            return parameters;
        }

        public List<ScannedConstraint> getReturnValueConstraints() {
            return returnValueConstraints;
        }

        public boolean isReturnValueCascading() {
            return returnValueCascading;
        }

        public void setReturnValueCascading(boolean returnValueCascading) {
            this.returnValueCascading = returnValueCascading;
        }

        public List<ScannedConstraint> getCrossParameterConstraints() {
            return crossParameterConstraints;
        }

        public List<ContainerElementConstraintInfo> getReturnValueContainerElementConstraints() {
            return returnValueContainerElementConstraints;
        }

        public Map<DotName, DotName> getReturnValueGroupConversions() {
            return returnValueGroupConversions;
        }

        public boolean isConstructor() {
            return constructor;
        }

        public boolean isGetter() {
            return getter;
        }

        public List<Type> getParameterTypes() {
            return parameterTypes;
        }

        public Type getReturnType() {
            return returnType;
        }
    }

    public static class ConstrainedParameter {

        private final String name;
        private final int index;
        private final Type parameterType;
        private final List<ScannedConstraint> constraints;
        private boolean cascading;
        private final List<ContainerElementConstraintInfo> containerElementConstraints;
        private final Map<DotName, DotName> groupConversions;

        public ConstrainedParameter(String name, int index, Type parameterType,
                List<ScannedConstraint> constraints, boolean cascading,
                List<ContainerElementConstraintInfo> containerElementConstraints,
                Map<DotName, DotName> groupConversions) {
            this.name = name;
            this.index = index;
            this.parameterType = parameterType;
            this.constraints = constraints != null ? constraints : new ArrayList<>();
            this.cascading = cascading;
            this.containerElementConstraints = containerElementConstraints != null
                    ? containerElementConstraints
                    : new ArrayList<>();
            this.groupConversions = groupConversions != null ? groupConversions : new LinkedHashMap<>();
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }

        public Type getParameterType() {
            return parameterType;
        }

        public List<ScannedConstraint> getConstraints() {
            return constraints;
        }

        public boolean isCascading() {
            return cascading;
        }

        public void setCascading(boolean cascading) {
            this.cascading = cascading;
        }

        public List<ContainerElementConstraintInfo> getContainerElementConstraints() {
            return containerElementConstraints;
        }

        public Map<DotName, DotName> getGroupConversions() {
            return groupConversions;
        }
    }

    public record ScannedConstraint(DotName annotationName, Map<String, Object> attributes, List<String> groups,
            List<String> payload, String validatorClassName, boolean reportAsSingleViolation,
            List<ScannedConstraint> composingConstraints,
            Map<String, Map<String, String>> overridesAttributes,
            List<String> allValidatorClassNames,
            Map<String, String> attributeTypes) {

        public ScannedConstraint(DotName annotationName, Map<String, Object> attributes,
                List<String> groups, List<String> payload, String validatorClassName,
                boolean reportAsSingleViolation, List<ScannedConstraint> composingConstraints) {
            this(annotationName, attributes, groups, payload, validatorClassName,
                    reportAsSingleViolation, composingConstraints, Collections.emptyMap(),
                    Collections.emptyList(), Collections.emptyMap());
        }

        public ScannedConstraint(DotName annotationName, Map<String, Object> attributes,
                List<String> groups, List<String> payload, String validatorClassName,
                boolean reportAsSingleViolation, List<ScannedConstraint> composingConstraints,
                Map<String, Map<String, String>> overridesAttributes,
                List<String> allValidatorClassNames,
                Map<String, String> attributeTypes) {
            this.annotationName = annotationName;
            this.attributes = attributes != null ? attributes : Collections.emptyMap();
            this.groups = groups != null ? groups : Collections.emptyList();
            this.payload = payload != null ? payload : Collections.emptyList();
            this.validatorClassName = validatorClassName;
            this.reportAsSingleViolation = reportAsSingleViolation;
            this.composingConstraints = composingConstraints != null
                    ? composingConstraints
                    : Collections.emptyList();
            this.overridesAttributes = overridesAttributes != null
                    ? overridesAttributes
                    : Collections.emptyMap();
            this.allValidatorClassNames = allValidatorClassNames != null
                    ? allValidatorClassNames
                    : Collections.emptyList();
            this.attributeTypes = attributeTypes != null
                    ? attributeTypes
                    : Collections.emptyMap();
        }
    }

    /**
     * Constraint information for a container element type argument,
     * e.g. {@code List<@NotNull String>} or {@code Map<@NotBlank String, @Valid Address>}.
     */
    public static class ContainerElementConstraintInfo {

        private final int typeArgumentIndex;
        private final List<ScannedConstraint> constraints;
        private boolean cascading;
        private final String containerClassName;
        private final Map<DotName, DotName> groupConversions;
        private final List<ContainerElementConstraintInfo> nestedContainerElements;

        public ContainerElementConstraintInfo(int typeArgumentIndex,
                List<ScannedConstraint> constraints, boolean cascading,
                String containerClassName) {
            this(typeArgumentIndex, constraints, cascading, containerClassName,
                    new LinkedHashMap<>(), new ArrayList<>());
        }

        public ContainerElementConstraintInfo(int typeArgumentIndex,
                List<ScannedConstraint> constraints, boolean cascading,
                String containerClassName, Map<DotName, DotName> groupConversions,
                List<ContainerElementConstraintInfo> nestedContainerElements) {
            this.typeArgumentIndex = typeArgumentIndex;
            this.constraints = constraints != null ? constraints : new ArrayList<>();
            this.cascading = cascading;
            this.containerClassName = containerClassName;
            this.groupConversions = groupConversions != null ? groupConversions : new LinkedHashMap<>();
            this.nestedContainerElements = nestedContainerElements != null
                    ? nestedContainerElements
                    : new ArrayList<>();
        }

        public int getTypeArgumentIndex() {
            return typeArgumentIndex;
        }

        public List<ScannedConstraint> getConstraints() {
            return constraints;
        }

        public boolean isCascading() {
            return cascading;
        }

        public void setCascading(boolean cascading) {
            this.cascading = cascading;
        }

        public String getContainerClassName() {
            return containerClassName;
        }

        public Map<DotName, DotName> getGroupConversions() {
            return groupConversions;
        }

        public List<ContainerElementConstraintInfo> getNestedContainerElements() {
            return nestedContainerElements;
        }
    }

    // ---------------------------------------------------------------------------
    // Mutators
    // ---------------------------------------------------------------------------

    public void addConstrainedField(DotName className, ConstrainedField field) {
        constrainedFields.computeIfAbsent(className, k -> new ArrayList<>()).add(field);
    }

    public void addConstrainedMethod(DotName className, ConstrainedMethod method) {
        constrainedMethods.computeIfAbsent(className, k -> new ArrayList<>()).add(method);
    }

    public void addConstrainedConstructor(DotName className, ConstrainedMethod ctor) {
        constrainedConstructors.computeIfAbsent(className, k -> new ArrayList<>()).add(ctor);
    }

    public void addClassLevelConstraint(DotName className, ScannedConstraint constraint) {
        classLevelConstraints.computeIfAbsent(className, k -> new ArrayList<>()).add(constraint);
    }

    public void addGroupSequence(DotName className, List<String> sequence) {
        groupSequences.put(className, sequence);
    }

    public void addCustomConstraintAnnotation(DotName annotationName) {
        customConstraintAnnotations.add(annotationName);
    }

    // ---------------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------------

    public Map<DotName, List<ConstrainedField>> getConstrainedFields() {
        return constrainedFields;
    }

    public Map<DotName, List<ConstrainedMethod>> getConstrainedMethods() {
        return constrainedMethods;
    }

    public Map<DotName, List<ConstrainedMethod>> getConstrainedConstructors() {
        return constrainedConstructors;
    }

    public Map<DotName, List<ScannedConstraint>> getClassLevelConstraints() {
        return classLevelConstraints;
    }

    public Map<DotName, List<String>> getGroupSequences() {
        return groupSequences;
    }

    public Set<DotName> getCustomConstraintAnnotations() {
        return customConstraintAnnotations;
    }

    public void addValidatedConstraintDefinition(String annotationClassName) {
        validatedConstraintDefinitions.add(annotationClassName);
    }

    public Set<String> getValidatedConstraintDefinitions() {
        return validatedConstraintDefinitions;
    }

    public void addCrossParameterValidatorClassName(String className) {
        crossParameterValidatorClassNames.add(className);
    }

    public Set<String> getCrossParameterValidatorClassNames() {
        return crossParameterValidatorClassNames;
    }

    public void addInterfaceGroupSequence(String interfaceName, List<String> sequence) {
        interfaceGroupSequences.put(interfaceName, sequence);
    }

    public Map<String, List<String>> getInterfaceGroupSequences() {
        return interfaceGroupSequences;
    }

    public void addConvertGroupDuplicateBean(String className) {
        convertGroupDuplicateBeans.add(className);
    }

    public Set<String> getConvertGroupDuplicateBeans() {
        return convertGroupDuplicateBeans;
    }

    public void addValidatorTargetType(String validatorClassName, String targetTypeName) {
        validatorTargetTypes.put(validatorClassName, targetTypeName);
    }

    public Map<String, String> getValidatorTargetTypes() {
        return validatorTargetTypes;
    }

    /**
     * Returns the union of all class names that appear in any constrained bucket.
     */
    public Set<DotName> getAllConstrainedClasses() {
        Set<DotName> all = new LinkedHashSet<>();
        all.addAll(constrainedFields.keySet());
        all.addAll(constrainedMethods.keySet());
        all.addAll(constrainedConstructors.keySet());
        all.addAll(classLevelConstraints.keySet());
        all.addAll(groupSequences.keySet());
        return all;
    }

    public void addTypeHierarchy(DotName className, List<String> hierarchy) {
        typeHierarchies.put(className, hierarchy);
    }

    public Map<DotName, List<String>> getTypeHierarchies() {
        return typeHierarchies;
    }

    public void addAllPropertyNames(DotName className, Set<String> propertyNames) {
        allPropertyNames.put(className, propertyNames);
    }

    public Map<DotName, Set<String>> getAllPropertyNames() {
        return allPropertyNames;
    }

    public void addConstraintValidatorMapping(String annotationClassName, List<String> validatorClassNames) {
        constraintValidatorMapping.put(annotationClassName, validatorClassNames);
    }

    public Map<String, List<String>> getConstraintValidatorMapping() {
        return constraintValidatorMapping;
    }

    public void addInvalidConstraintDefinition(String annotationClassName, String errorMessage) {
        invalidConstraintDefinitions.put(annotationClassName, errorMessage);
    }

    public Map<String, String> getInvalidConstraintDefinitions() {
        return invalidConstraintDefinitions;
    }

    public void addInvalidConstraintDeclaration(String annotationClassName, String errorMessage) {
        invalidConstraintDeclarations.put(annotationClassName, errorMessage);
    }

    public Map<String, String> getInvalidConstraintDeclarations() {
        return invalidConstraintDeclarations;
    }

    public void addConvertGroupDuplicateError(String beanClassName, String errorMessage) {
        convertGroupDuplicateErrors.put(beanClassName, errorMessage);
    }

    public Map<String, String> getConvertGroupDuplicateErrors() {
        return convertGroupDuplicateErrors;
    }

    public void addIterableTypeArgument(String containerClassName, String elementTypeName) {
        iterableTypeArguments.put(containerClassName, elementTypeName);
    }

    public Map<String, String> getIterableTypeArguments() {
        return iterableTypeArguments;
    }
}
