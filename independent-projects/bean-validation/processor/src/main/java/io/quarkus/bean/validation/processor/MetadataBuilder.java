package io.quarkus.bean.validation.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.DotName;

import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedBeanMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedFieldMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedMethodMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedParameterMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstraintMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ContainerElementConstraint;

/**
 * Converts a {@link ScanResult} (Jandex-based) into the runtime metadata POJOs
 * defined in {@code io.quarkus.bean.validation.metadata.model}.
 * <p>
 * The output has no dependency on Jandex and can be consumed directly by the
 * runtime validation engine.
 */
class MetadataBuilder {

    private final ConstraintScanner constraintScanner;

    MetadataBuilder(ConstraintScanner constraintScanner) {
        this.constraintScanner = constraintScanner;
    }

    BeanValidationMetadata build(ScanResult scanResult) {
        Map<String, ConstrainedBeanMetadata> beans = new LinkedHashMap<>();

        // Build metadata for all constrained classes
        for (DotName className : scanResult.getAllConstrainedClasses()) {
            ConstrainedBeanMetadata beanMeta = buildBeanMetadata(className, scanResult);
            beans.put(className.toString(), beanMeta);
        }

        // Also build metadata for subclasses/implementors that have type hierarchies
        // but aren't directly constrained (they inherit constraints through their hierarchy)
        for (DotName className : scanResult.getTypeHierarchies().keySet()) {
            if (!beans.containsKey(className.toString())) {
                ConstrainedBeanMetadata beanMeta = buildBeanMetadata(className, scanResult);
                beans.put(className.toString(), beanMeta);
            }
        }

        // Compute beans that passed @ConvertGroup duplicate check at build time:
        // all constrained classes minus those where duplicates were found
        Set<String> convertGroupValidatedBeans = new LinkedHashSet<>();
        for (DotName className : scanResult.getAllConstrainedClasses()) {
            String name = className.toString();
            if (!scanResult.getConvertGroupDuplicateBeans().contains(name)) {
                convertGroupValidatedBeans.add(name);
            }
        }

        return new BeanValidationMetadata(beans,
                scanResult.getValidatedConstraintDefinitions(),
                scanResult.getCrossParameterValidatorClassNames(),
                scanResult.getInterfaceGroupSequences(),
                convertGroupValidatedBeans,
                scanResult.getValidatorTargetTypes(),
                scanResult.getConstraintValidatorMapping(),
                scanResult.getInvalidConstraintDefinitions(),
                scanResult.getInvalidConstraintDeclarations(),
                scanResult.getConvertGroupDuplicateErrors(),
                scanResult.getIterableTypeArguments());
    }

    // ---------------------------------------------------------------------------
    // Bean-level conversion
    // ---------------------------------------------------------------------------

    private ConstrainedBeanMetadata buildBeanMetadata(DotName className, ScanResult scanResult) {
        // Class-level constraints
        List<ConstraintMetadata> classConstraints = convertConstraints(
                scanResult.getClassLevelConstraints().getOrDefault(className, Collections.emptyList()));

        // Field metadata
        List<ConstrainedFieldMetadata> fields = scanResult.getConstrainedFields()
                .getOrDefault(className, Collections.emptyList())
                .stream()
                .map(this::convertField)
                .collect(Collectors.toList());

        // Method metadata
        List<ConstrainedMethodMetadata> methods = scanResult.getConstrainedMethods()
                .getOrDefault(className, Collections.emptyList())
                .stream()
                .map(this::convertMethod)
                .collect(Collectors.toList());

        // Constructor metadata
        List<ConstrainedMethodMetadata> constructors = scanResult.getConstrainedConstructors()
                .getOrDefault(className, Collections.emptyList())
                .stream()
                .map(this::convertMethod)
                .collect(Collectors.toList());

        // Group sequence
        List<String> groupSequence = scanResult.getGroupSequences().get(className);

        // Pre-computed type hierarchy
        List<String> typeHierarchy = scanResult.getTypeHierarchies().get(className);

        // Pre-computed all property names
        Set<String> allPropertyNames = scanResult.getAllPropertyNames().get(className);

        return new ConstrainedBeanMetadata(className.toString(), classConstraints, fields,
                methods, constructors, groupSequence, typeHierarchy, allPropertyNames);
    }

    // ---------------------------------------------------------------------------
    // Field conversion
    // ---------------------------------------------------------------------------

    private ConstrainedFieldMetadata convertField(ScanResult.ConstrainedField field) {
        List<ConstraintMetadata> constraints = convertConstraints(field.getConstraints());
        List<ContainerElementConstraint> containerElements = convertContainerElementConstraints(
                field.getContainerElementConstraints());
        Map<String, String> groupConversions = convertGroupConversions(field.getGroupConversions());

        return new ConstrainedFieldMetadata(
                field.getFieldName(),
                field.getDeclaringClass().toString(),
                field.getFieldType().name().toString(),
                constraints,
                field.isCascading(),
                containerElements,
                groupConversions);
    }

    // ---------------------------------------------------------------------------
    // Method / constructor conversion
    // ---------------------------------------------------------------------------

    private ConstrainedMethodMetadata convertMethod(ScanResult.ConstrainedMethod method) {
        List<ConstrainedParameterMetadata> parameters = method.getParameters().stream()
                .map(this::convertParameter)
                .collect(Collectors.toList());

        List<ConstraintMetadata> returnValueConstraints = convertConstraints(
                method.getReturnValueConstraints());
        List<ConstraintMetadata> crossParameterConstraints = convertConstraints(
                method.getCrossParameterConstraints());
        List<ContainerElementConstraint> returnValueContainerElements = convertContainerElementConstraints(
                method.getReturnValueContainerElementConstraints());
        Map<String, String> returnValueGroupConversions = convertGroupConversions(
                method.getReturnValueGroupConversions());

        List<String> parameterTypeNames = new ArrayList<>();
        for (org.jboss.jandex.Type paramType : method.getParameterTypes()) {
            parameterTypeNames.add(paramType.name().toString());
        }

        String returnTypeName = method.getReturnType() != null
                ? method.getReturnType().name().toString()
                : method.getDeclaringClass().toString();

        return new ConstrainedMethodMetadata(
                method.getMethodName(),
                method.getDeclaringClass().toString(),
                returnTypeName,
                parameterTypeNames,
                parameters,
                returnValueConstraints,
                method.isReturnValueCascading(),
                crossParameterConstraints,
                returnValueContainerElements,
                returnValueGroupConversions,
                method.isConstructor(),
                method.isGetter());
    }

    // ---------------------------------------------------------------------------
    // Parameter conversion
    // ---------------------------------------------------------------------------

    private ConstrainedParameterMetadata convertParameter(ScanResult.ConstrainedParameter param) {
        List<ConstraintMetadata> constraints = convertConstraints(param.getConstraints());
        List<ContainerElementConstraint> containerElements = convertContainerElementConstraints(
                param.getContainerElementConstraints());
        Map<String, String> groupConversions = convertGroupConversions(param.getGroupConversions());

        return new ConstrainedParameterMetadata(
                param.getName(),
                param.getIndex(),
                param.getParameterType().name().toString(),
                constraints,
                param.isCascading(),
                containerElements,
                groupConversions);
    }

    // ---------------------------------------------------------------------------
    // Constraint conversion
    // ---------------------------------------------------------------------------

    private List<ConstraintMetadata> convertConstraints(List<ScanResult.ScannedConstraint> scanned) {
        if (scanned == null || scanned.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConstraintMetadata> result = new ArrayList<>(scanned.size());
        for (ScanResult.ScannedConstraint sc : scanned) {
            result.add(convertConstraint(sc));
        }
        return result;
    }

    private ConstraintMetadata convertConstraint(ScanResult.ScannedConstraint scanned) {
        List<ConstraintMetadata> composing = Collections.emptyList();
        if (scanned.composingConstraints() != null && !scanned.composingConstraints().isEmpty()) {
            composing = scanned.composingConstraints().stream()
                    .map(this::convertConstraint)
                    .collect(Collectors.toList());
        }

        Map<String, Map<String, String>> overrides = scanned.overridesAttributes();
        String defaultMessage = constraintScanner != null
                ? constraintScanner.extractDefaultMessageTemplate(scanned.annotationName())
                : null;

        return new ConstraintMetadata(
                scanned.annotationName().toString(),
                scanned.attributes(),
                scanned.groups(),
                scanned.payload(),
                scanned.validatorClassName(),
                scanned.reportAsSingleViolation(),
                composing,
                overrides,
                defaultMessage,
                scanned.allValidatorClassNames(),
                scanned.attributeTypes());
    }

    // ---------------------------------------------------------------------------
    // Container element constraint conversion
    // ---------------------------------------------------------------------------

    private List<ContainerElementConstraint> convertContainerElementConstraints(
            List<ScanResult.ContainerElementConstraintInfo> ceInfos) {
        if (ceInfos == null || ceInfos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContainerElementConstraint> result = new ArrayList<>(ceInfos.size());
        for (ScanResult.ContainerElementConstraintInfo ceInfo : ceInfos) {
            result.add(convertContainerElementConstraint(ceInfo));
        }
        return result;
    }

    private ContainerElementConstraint convertContainerElementConstraint(
            ScanResult.ContainerElementConstraintInfo ceInfo) {
        List<ConstraintMetadata> constraints = convertConstraints(ceInfo.getConstraints());
        Map<String, String> groupConversions = convertGroupConversions(ceInfo.getGroupConversions());
        List<ContainerElementConstraint> nested = convertContainerElementConstraints(
                ceInfo.getNestedContainerElements());

        return new ContainerElementConstraint(
                ceInfo.getTypeArgumentIndex(),
                constraints,
                ceInfo.isCascading(),
                ceInfo.getContainerClassName(),
                groupConversions,
                nested);
    }

    // ---------------------------------------------------------------------------
    // Group conversion helpers
    // ---------------------------------------------------------------------------

    private Map<String, String> convertGroupConversions(Map<DotName, DotName> groupConversions) {
        if (groupConversions == null || groupConversions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>(groupConversions.size());
        for (Map.Entry<DotName, DotName> entry : groupConversions.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return result;
    }
}
