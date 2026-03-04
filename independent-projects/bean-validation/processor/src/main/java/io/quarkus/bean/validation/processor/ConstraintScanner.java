package io.quarkus.bean.validation.processor;

import static jakarta.validation.constraintvalidation.ValidationTarget.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintTarget;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeTarget;
import org.jboss.logging.Logger;

class ConstraintScanner {

    private static final Logger LOG = Logger.getLogger(ConstraintScanner.class);

    private static final String INIT_METHOD_NAME = "<init>";
    private static final String SVT_PARAMETERS = PARAMETERS.name();
    private static final String SVT_ANNOTATED_ELEMENT = ANNOTATED_ELEMENT.name();

    private final IndexView index;
    private final ValidatorResolver validatorResolver;

    ConstraintScanner(IndexView index, ValidatorResolver validatorResolver) {
        this.index = index;
        this.validatorResolver = validatorResolver;
    }

    ScanResult scan() {
        ScanResult result = new ScanResult();

        // 1. Discover custom constraint annotations (annotated with @Constraint)
        discoverCustomConstraints(result);

        // 2. Build the complete set of constraint annotations (built-in + custom)
        Set<DotName> allConstraints = new HashSet<>(DotNames.BUILT_IN_CONSTRAINTS);
        allConstraints.addAll(result.getCustomConstraintAnnotations());

        // 3. For each constraint annotation scan all usages in the index
        for (DotName constraintDotName : allConstraints) {
            scanConstraintUsages(constraintDotName, result);
        }

        // 4. Scan repeatable container annotations for every constraint that has one
        for (DotName constraintDotName : new HashSet<>(allConstraints)) {
            scanRepeatableContainerUsages(constraintDotName, result);
        }

        // 5. Scan for @Valid (cascading) on fields, methods, and parameters
        scanCascading(result);

        // 6. Scan for @GroupSequence
        scanGroupSequences(result);

        // 7. Scan for @ConvertGroup
        scanConvertGroups(result);

        // 8. Validate constraint definitions at build time
        validateConstraintDefinitions(allConstraints, result);

        // 9. Scan interface group sequences
        scanInterfaceGroupSequences(result);

        // 10. Discover cross-parameter validators
        scanCrossParameterValidators(result);

        // 11. Scan validator target types for caching
        scanValidatorTargetTypes(allConstraints, result);

        // 12. Pre-compute type hierarchies for constrained beans
        scanTypeHierarchies(result);

        // 13. Pre-compute all property names for constrained beans
        scanAllPropertyNames(result);

        // 14. Build constraint-to-validator mapping
        scanConstraintValidatorMapping(allConstraints, result);

        // 15. Pre-compute iterable type arguments for constrained beans
        scanIterableTypeArguments(result);

        return result;
    }

    // ---------------------------------------------------------------------------
    // 1. Custom constraint discovery
    // ---------------------------------------------------------------------------

    private void discoverCustomConstraints(ScanResult result) {
        for (AnnotationInstance constraintMeta : index.getAnnotations(DotNames.CONSTRAINT)) {
            if (constraintMeta.target().kind() == AnnotationTarget.Kind.CLASS) {
                DotName customAnnotation = constraintMeta.target().asClass().name();
                result.addCustomConstraintAnnotation(customAnnotation);
                LOG.debugf("Discovered custom constraint annotation: %s", customAnnotation);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 3. Direct constraint usages
    // ---------------------------------------------------------------------------

    private void scanConstraintUsages(DotName constraintDotName, ScanResult result) {
        Collection<AnnotationInstance> usages = index.getAnnotations(constraintDotName);
        for (AnnotationInstance annotation : usages) {
            processAnnotationUsage(annotation, constraintDotName, result);
        }
    }

    // ---------------------------------------------------------------------------
    // 4. Repeatable container annotations
    // ---------------------------------------------------------------------------

    private void scanRepeatableContainerUsages(DotName constraintDotName, ScanResult result) {
        DotName containerDotName = resolveRepeatableContainer(constraintDotName);
        if (containerDotName == null) {
            return;
        }

        Collection<AnnotationInstance> containerUsages = index.getAnnotations(containerDotName);
        for (AnnotationInstance containerAnnotation : containerUsages) {
            AnnotationValue valueAttr = containerAnnotation.value();
            if (valueAttr == null) {
                continue;
            }
            AnnotationInstance[] nested = valueAttr.asNestedArray();
            for (AnnotationInstance nestedAnnotation : nested) {
                // The nested annotation targets the same element as the container
                processAnnotationUsage(
                        AnnotationInstance.create(nestedAnnotation.name(), containerAnnotation.target(),
                                nestedAnnotation.values()),
                        constraintDotName, result);
            }
        }
    }

    /**
     * Resolves the repeatable container annotation for the given constraint
     * using the Jandex index. The jakarta.validation-api JAR must be indexed.
     */
    private DotName resolveRepeatableContainer(DotName constraintDotName) {
        ClassInfo constraintAnnotationClass = index.getClassByName(constraintDotName);
        if (constraintAnnotationClass == null) {
            LOG.debugv("Constraint annotation not found in Jandex index: {0}", constraintDotName);
            return null;
        }

        AnnotationInstance repeatableMeta = constraintAnnotationClass.annotation(DotNames.REPEATABLE);
        if (repeatableMeta != null) {
            Type containerType = repeatableMeta.value().asClass();
            return containerType.name();
        }
        // Fall back to BV-spec convention: inner @interface List (without @Repeatable)
        // This is the standard pattern from BV spec section 3.2
        DotName listContainer = DotName.createSimple(constraintDotName.toString() + "$List");
        ClassInfo listClass = index.getClassByName(listContainer);
        if (listClass != null) {
            return listContainer;
        }
        // Fall back to scanning for a separate top-level container annotation whose
        // value() method returns an array of this constraint type. This handles
        // the older BV-spec multi-valued constraint pattern where the container is
        // a standalone annotation (e.g., @AlwaysValidList containing AlwaysValid[])
        // rather than an inner @interface List or linked via @Repeatable.
        return findExternalContainerAnnotation(constraintDotName);
    }

    /**
     * Scans the Jandex index for an annotation class that acts as a container for the
     * given constraint annotation. A container is identified by having a {@code value()}
     * method that returns an array of the constraint annotation type.
     * <p>
     * This covers the BV-spec multi-valued constraint pattern where the container is a
     * separate top-level annotation (not an inner {@code List} class and not linked via
     * {@code @Repeatable}). For example:
     *
     * <pre>
     * &#64;interface AlwaysValidList {
     *     AlwaysValid[] value();
     * }
     * </pre>
     */
    private DotName findExternalContainerAnnotation(DotName constraintDotName) {
        for (ClassInfo candidate : index.getKnownClasses()) {
            if (!java.lang.reflect.Modifier.isInterface(candidate.flags())
                    || !candidate.isAnnotation()) {
                continue;
            }
            // Skip the constraint annotation itself
            if (candidate.name().equals(constraintDotName)) {
                continue;
            }
            MethodInfo valueMethod = candidate.method("value");
            if (valueMethod == null) {
                continue;
            }
            Type returnType = valueMethod.returnType();
            // Check if the return type is an array of the constraint annotation
            if (returnType.kind() == Type.Kind.ARRAY
                    && returnType.asArrayType().componentType().name().equals(constraintDotName)) {
                return candidate.name();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------
    // Annotation usage dispatcher
    // ---------------------------------------------------------------------------

    private void processAnnotationUsage(AnnotationInstance annotation, DotName constraintDotName,
            ScanResult result) {
        AnnotationTarget target = annotation.target();
        if (target == null) {
            return;
        }

        switch (target.kind()) {
            case FIELD:
                processFieldAnnotation(annotation, constraintDotName, target.asField(), result);
                break;
            case METHOD:
                processMethodAnnotation(annotation, constraintDotName, target.asMethod(), result);
                break;
            case METHOD_PARAMETER:
                processParameterAnnotation(annotation, constraintDotName, target.asMethodParameter(), result);
                break;
            case CLASS:
                processClassAnnotation(annotation, constraintDotName, target.asClass(), result);
                break;
            case TYPE:
                processTypeAnnotation(annotation, constraintDotName, target.asType(), result);
                break;
            default:
                LOG.debugf("Ignoring constraint %s on unsupported target kind %s",
                        constraintDotName, target.kind());
                break;
        }
    }

    // ---------------------------------------------------------------------------
    // FIELD annotations
    // ---------------------------------------------------------------------------

    private void processFieldAnnotation(AnnotationInstance annotation, DotName constraintDotName,
            FieldInfo field, ScanResult result) {
        ScanResult.ScannedConstraint constraint = buildScannedConstraint(annotation, constraintDotName, field.type());
        DotName declaringClass = field.declaringClass().name();

        ScanResult.ConstrainedField existing = findExistingConstrainedField(result, declaringClass, field.name());
        if (existing != null) {
            existing.getConstraints().add(constraint);
        } else {
            List<ScanResult.ScannedConstraint> constraints = new ArrayList<>();
            constraints.add(constraint);
            result.addConstrainedField(declaringClass, new ScanResult.ConstrainedField(
                    field.name(), field.type(), declaringClass,
                    constraints, false, new ArrayList<>(), new LinkedHashMap<>()));
        }
    }

    // ---------------------------------------------------------------------------
    // METHOD annotations (return value constraints)
    // ---------------------------------------------------------------------------

    private void processMethodAnnotation(AnnotationInstance annotation, DotName constraintDotName,
            MethodInfo method, ScanResult result) {
        ScanResult.ScannedConstraint constraint = buildScannedConstraint(annotation, constraintDotName, method.returnType());

        // Determine if this is a cross-parameter or return value constraint
        ValidationTarget validationTarget = validatorResolver.determineValidationTarget(constraintDotName, annotation);
        boolean isCrossParameter = validationTarget == ValidationTarget.PARAMETERS;
        // For BOTH (implicit), classify based on method context:
        // - methods with parameters and no return type → cross-parameter
        // - otherwise → return value (runtime will handle resolution)
        if (validationTarget == ValidationTarget.BOTH) {
            // Default to cross-parameter for methods with parameters (per spec 10.1.2)
            if (method.parametersCount() > 0 && method.returnType().kind() == org.jboss.jandex.Type.Kind.VOID) {
                isCrossParameter = true;
            }
        }

        ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
        if (isCrossParameter) {
            existing.getCrossParameterConstraints().add(constraint);
        } else {
            existing.getReturnValueConstraints().add(constraint);
        }
    }

    // ---------------------------------------------------------------------------
    // PARAMETER annotations
    // ---------------------------------------------------------------------------

    private void processParameterAnnotation(AnnotationInstance annotation, DotName constraintDotName,
            MethodParameterInfo paramInfo, ScanResult result) {
        ScanResult.ScannedConstraint constraint = buildScannedConstraint(annotation, constraintDotName,
                paramInfo.method().parameterType(paramInfo.position()));
        MethodInfo method = paramInfo.method();
        short paramIndex = paramInfo.position();
        String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;

        ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
        ScanResult.ConstrainedParameter param = findOrCreateParameter(
                existing, paramIndex, paramName, method.parameterType(paramIndex));
        param.getConstraints().add(constraint);
    }

    // ---------------------------------------------------------------------------
    // CLASS-level annotations
    // ---------------------------------------------------------------------------

    private void processClassAnnotation(AnnotationInstance annotation, DotName constraintDotName,
            ClassInfo classInfo, ScanResult result) {
        ScanResult.ScannedConstraint constraint = buildScannedConstraint(annotation, constraintDotName);
        result.addClassLevelConstraint(classInfo.name(), constraint);
    }

    // ---------------------------------------------------------------------------
    // TYPE-use annotations (container element constraints)
    // ---------------------------------------------------------------------------

    private void processTypeAnnotation(AnnotationInstance annotation, DotName constraintDotName,
            AnnotationTarget typeTarget, ScanResult result) {
        // Type-use annotations can appear on type arguments of fields, method return
        // types, or method parameter types. We need to walk up to find the enclosing
        // element.
        ScanResult.ScannedConstraint constraint = buildScannedConstraint(annotation, constraintDotName);

        // Jandex represents TYPE targets with an AnnotationTarget whose enclosingTarget()
        // gives us the field/method/parameter the type belongs to
        AnnotationTarget enclosing = typeTarget.asType().enclosingTarget();
        if (enclosing == null) {
            LOG.debugf("Type annotation %s has no enclosing target, skipping", constraintDotName);
            return;
        }

        // Check for nested type arguments first (e.g., Map<K, List<@NotNull V>>).
        // If the annotated type is not a direct type argument of the enclosing type,
        // it might be nested within a parameterized type argument.
        if (tryHandleNestedTypeConstraint(enclosing, typeTarget.asType(), constraint, result)) {
            return;
        }

        // Determine the type argument index from the Type position.
        // Returns -1 if the annotation is on the container type itself (not a type argument),
        // in which case this is a direct constraint, not a container element constraint.
        int typeArgIndex = determineTypeArgumentIndex(typeTarget.asType(), annotation);
        if (typeArgIndex < 0) {
            // The annotation is on the container type itself (e.g., @Size List<String>).
            // Skip: this is handled as a direct field/method/parameter constraint.
            return;
        }
        String containerClassName = determineContainerClassName(typeTarget.asType());

        // Compute number of type arguments on the enclosing parameterized type.
        // Used to detect Jandex type deduplication (e.g., Map<@NotNull String, @NotNull String>)
        Type enclosingTypeForArgs = getEnclosingFieldOrReturnType(enclosing);
        int numTypeArgs = enclosingTypeForArgs != null
                && enclosingTypeForArgs.kind() == Type.Kind.PARAMETERIZED_TYPE
                        ? enclosingTypeForArgs.asParameterizedType().arguments().size()
                        : -1;

        switch (enclosing.kind()) {
            case FIELD: {
                FieldInfo field = enclosing.asField();
                ScanResult.ConstrainedField existing = getOrCreateConstrainedField(result, field);
                addContainerElementConstraint(existing.getContainerElementConstraints(),
                        typeArgIndex, constraint, containerClassName, numTypeArgs);
                break;
            }
            case METHOD: {
                MethodInfo method = enclosing.asMethod();
                ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                // Jandex reports TYPE annotations on method parameter type arguments
                // with enclosing=METHOD (not METHOD_PARAMETER). We need to check if
                // the annotated type is within a parameter type or the return type.
                TypeTarget tt = typeTarget.asType();
                int paramIdx = findParameterContainingType(method, tt);
                if (paramIdx >= 0) {
                    // The type annotation is on a parameter's type argument
                    String paramName = method.parameterName(paramIdx) != null
                            ? method.parameterName(paramIdx)
                            : "arg" + paramIdx;
                    // Re-determine typeArgIndex relative to the parameter type
                    Type paramType = method.parameterType(paramIdx);
                    int paramTypeArgIdx = determineTypeArgumentIndexIn(tt.target(), paramType);
                    String paramContainerClassName = paramType.name().toString();
                    int paramNumTypeArgs = paramType.kind() == Type.Kind.PARAMETERIZED_TYPE
                            ? paramType.asParameterizedType().arguments().size()
                            : -1;
                    ScanResult.ConstrainedParameter param = findOrCreateParameter(
                            existing, paramIdx, paramName, paramType);
                    addContainerElementConstraint(param.getContainerElementConstraints(),
                            paramTypeArgIdx, constraint, paramContainerClassName, paramNumTypeArgs);
                } else {
                    addContainerElementConstraint(
                            existing.getReturnValueContainerElementConstraints(),
                            typeArgIndex, constraint, containerClassName, numTypeArgs);
                }
                break;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                MethodInfo method = paramInfo.method();
                short paramIndex = paramInfo.position();
                String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;

                ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                ScanResult.ConstrainedParameter param = findOrCreateParameter(
                        existingMethod, paramIndex, paramName,
                        method.parameterType(paramIndex));
                addContainerElementConstraint(param.getContainerElementConstraints(),
                        typeArgIndex, constraint, containerClassName, numTypeArgs);
                break;
            }
            default:
                LOG.debugf("Ignoring type annotation %s on unsupported enclosing target kind %s",
                        constraintDotName, enclosing.kind());
                break;
        }
    }

    // ---------------------------------------------------------------------------
    // 5. @Valid cascading
    // ---------------------------------------------------------------------------

    private void scanCascading(ScanResult result) {
        Collection<AnnotationInstance> validAnnotations = index.getAnnotations(DotNames.VALID);
        for (AnnotationInstance annotation : validAnnotations) {
            AnnotationTarget target = annotation.target();
            if (target == null) {
                continue;
            }
            switch (target.kind()) {
                case FIELD: {
                    FieldInfo field = target.asField();
                    ScanResult.ConstrainedField existing = getOrCreateConstrainedField(result, field);
                    existing.setCascading(true);
                    break;
                }
                case METHOD: {
                    MethodInfo method = target.asMethod();
                    ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                    existing.setReturnValueCascading(true);
                    break;
                }
                case METHOD_PARAMETER: {
                    MethodParameterInfo paramInfo = target.asMethodParameter();
                    MethodInfo method = paramInfo.method();
                    short paramIndex = paramInfo.position();
                    String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;

                    ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                    ScanResult.ConstrainedParameter param = findOrCreateParameter(
                            existingMethod, paramIndex, paramName,
                            method.parameterType(paramIndex));
                    param.setCascading(true);
                    break;
                }
                case TYPE: {
                    // @Valid on a type argument means cascading for that container element
                    AnnotationTarget enclosing = target.asType().enclosingTarget();
                    if (enclosing == null) {
                        break;
                    }
                    // Check for nested @Valid first (e.g., Map<K, List<@Valid V>>)
                    if (tryHandleNestedValidCascading(enclosing, target.asType(), result)) {
                        break;
                    }
                    int typeArgIdx = determineTypeArgumentIndex(target.asType());
                    if (typeArgIdx < 0) {
                        // @Valid is on the container type itself — treat as direct cascading
                        // (already handled by FIELD/METHOD/METHOD_PARAMETER cases above)
                        break;
                    }
                    String containerClass = determineContainerClassName(target.asType());
                    // Handle Jandex quirk: @Valid on parameter type arg may have METHOD enclosing
                    if (enclosing.kind() == AnnotationTarget.Kind.METHOD) {
                        MethodInfo method = enclosing.asMethod();
                        int paramIdx = findParameterContainingType(method, target.asType());
                        if (paramIdx >= 0) {
                            Type paramType = method.parameterType(paramIdx);
                            int paramTypeArgIdx = determineTypeArgumentIndexIn(
                                    target.asType().target(), paramType);
                            String paramContainer = paramType.name().toString();
                            markContainerElementCascadingOnParam(method, paramIdx, paramTypeArgIdx,
                                    paramContainer, result);
                            break;
                        }
                    }
                    markContainerElementCascading(enclosing, typeArgIdx, containerClass, result);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void markContainerElementCascading(AnnotationTarget enclosing, int typeArgIdx,
            String containerClass, ScanResult result) {
        switch (enclosing.kind()) {
            case FIELD: {
                FieldInfo field = enclosing.asField();
                ScanResult.ConstrainedField existing = getOrCreateConstrainedField(result, field);
                markOrCreateContainerElementCascading(
                        existing.getContainerElementConstraints(), typeArgIdx, containerClass);
                break;
            }
            case METHOD: {
                MethodInfo method = enclosing.asMethod();
                ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                markOrCreateContainerElementCascading(
                        existing.getReturnValueContainerElementConstraints(), typeArgIdx, containerClass);
                break;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                MethodInfo method = paramInfo.method();
                short paramIndex = paramInfo.position();
                String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;

                ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                ScanResult.ConstrainedParameter param = findOrCreateParameter(
                        existingMethod, paramIndex, paramName,
                        method.parameterType(paramIndex));
                markOrCreateContainerElementCascading(
                        param.getContainerElementConstraints(), typeArgIdx, containerClass);
                break;
            }
            default:
                break;
        }
    }

    /**
     * Marks container element cascading on a method parameter (handles Jandex quirk where
     *
     * @Valid on parameter type argument has METHOD as enclosing target).
     */
    private void markContainerElementCascadingOnParam(MethodInfo method, int paramIdx,
            int typeArgIdx, String containerClass, ScanResult result) {
        ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
        String paramName = method.parameterName(paramIdx) != null
                ? method.parameterName(paramIdx)
                : "arg" + paramIdx;
        ScanResult.ConstrainedParameter param = findOrCreateParameter(
                existing, paramIdx, paramName, method.parameterType(paramIdx));
        markOrCreateContainerElementCascading(
                param.getContainerElementConstraints(), typeArgIdx, containerClass);
    }

    private void markOrCreateContainerElementCascading(
            List<ScanResult.ContainerElementConstraintInfo> ceList,
            int typeArgIdx, String containerClass) {
        for (ScanResult.ContainerElementConstraintInfo ce : ceList) {
            if (ce.getTypeArgumentIndex() == typeArgIdx) {
                ce.setCascading(true);
                return;
            }
        }
        // No existing container element entry, create one with cascading=true
        ceList.add(new ScanResult.ContainerElementConstraintInfo(
                typeArgIdx, new ArrayList<>(), true, containerClass));
    }

    /**
     * Handles @Valid on nested type arguments (e.g., {@code Map<K, List<@Valid V>>}).
     * Returns true if handled as nested, false otherwise.
     */
    private boolean tryHandleNestedValidCascading(AnnotationTarget enclosing,
            TypeTarget typeTarget, ScanResult result) {
        Type annotatedType = typeTarget.target();

        switch (enclosing.kind()) {
            case FIELD: {
                Type fieldType = enclosing.asField().type();
                if (isDirectTypeArgument(fieldType, annotatedType)) {
                    return false;
                }
                List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(fieldType, annotatedType);
                if (path == null || path.size() < 2) {
                    return false;
                }
                List<ScanResult.ContainerElementConstraintInfo> ceList = getContainerElementListForCascading(enclosing, result);
                if (ceList != null) {
                    markNestedContainerElementCascading(ceList, path);
                }
                return true;
            }
            case METHOD: {
                MethodInfo method = enclosing.asMethod();
                int paramIdx = findParameterContainingType(method, typeTarget);
                if (paramIdx >= 0) {
                    Type paramType = method.parameterType(paramIdx);
                    if (isDirectTypeArgument(paramType, annotatedType)) {
                        return false;
                    }
                    List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(paramType, annotatedType);
                    if (path == null || path.size() < 2) {
                        return false;
                    }
                    // Get the parameter's container element list
                    ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                    String paramName = method.parameterName(paramIdx) != null
                            ? method.parameterName(paramIdx)
                            : "arg" + paramIdx;
                    ScanResult.ConstrainedParameter param = findOrCreateParameter(
                            existing, paramIdx, paramName, paramType);
                    markNestedContainerElementCascading(
                            param.getContainerElementConstraints(), path);
                    return true;
                }
                // Check return type
                Type returnType = method.returnType();
                if (isDirectTypeArgument(returnType, annotatedType)) {
                    return false;
                }
                List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(returnType, annotatedType);
                if (path == null || path.size() < 2) {
                    return false;
                }
                List<ScanResult.ContainerElementConstraintInfo> ceList = getContainerElementListForCascading(enclosing, result);
                if (ceList != null) {
                    markNestedContainerElementCascading(ceList, path);
                }
                return true;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                Type paramType = paramInfo.method().parameterType(paramInfo.position());
                if (isDirectTypeArgument(paramType, annotatedType)) {
                    return false;
                }
                List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(paramType, annotatedType);
                if (path == null || path.size() < 2) {
                    return false;
                }
                List<ScanResult.ContainerElementConstraintInfo> ceList = getContainerElementListForCascading(enclosing, result);
                if (ceList != null) {
                    markNestedContainerElementCascading(ceList, path);
                }
                return true;
            }
            default:
                return false;
        }
    }

    private List<ScanResult.ContainerElementConstraintInfo> getContainerElementListForCascading(
            AnnotationTarget enclosing, ScanResult result) {
        switch (enclosing.kind()) {
            case FIELD: {
                return getOrCreateConstrainedField(result, enclosing.asField())
                        .getContainerElementConstraints();
            }
            case METHOD: {
                return getOrCreateConstrainedMethod(result, enclosing.asMethod())
                        .getReturnValueContainerElementConstraints();
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                MethodInfo method = paramInfo.method();
                short paramIndex = paramInfo.position();
                String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;
                ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                ScanResult.ConstrainedParameter param = findOrCreateParameter(
                        existingMethod, paramIndex, paramName,
                        method.parameterType(paramIndex));
                return param.getContainerElementConstraints();
            }
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------------------
    // 6. @GroupSequence
    // ---------------------------------------------------------------------------

    private void scanGroupSequences(ScanResult result) {
        Collection<AnnotationInstance> gsAnnotations = index.getAnnotations(DotNames.GROUP_SEQUENCE);
        for (AnnotationInstance annotation : gsAnnotations) {
            AnnotationTarget target = annotation.target();
            if (target == null || target.kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            AnnotationValue valueAttr = annotation.value();
            if (valueAttr == null) {
                continue;
            }
            Type[] groupTypes = valueAttr.asClassArray();
            List<String> sequence = new ArrayList<>(groupTypes.length);
            for (Type groupType : groupTypes) {
                sequence.add(groupType.name().toString());
            }
            result.addGroupSequence(target.asClass().name(), sequence);
        }
    }

    // ---------------------------------------------------------------------------
    // 7. @ConvertGroup / @ConvertGroup.List
    // ---------------------------------------------------------------------------

    private void scanConvertGroups(ScanResult result) {
        // Scan single @ConvertGroup
        for (AnnotationInstance annotation : index.getAnnotations(DotNames.CONVERT_GROUP)) {
            processConvertGroup(annotation, result);
        }
        // Scan @ConvertGroup.List (repeatable container)
        for (AnnotationInstance listAnnotation : index.getAnnotations(DotNames.CONVERT_GROUP_LIST)) {
            AnnotationValue valueAttr = listAnnotation.value();
            if (valueAttr == null) {
                continue;
            }
            AnnotationInstance[] nested = valueAttr.asNestedArray();
            for (AnnotationInstance nestedCg : nested) {
                // Re-target the nested annotation to the container's target
                AnnotationInstance retargeted = AnnotationInstance.create(
                        nestedCg.name(), listAnnotation.target(), nestedCg.values());
                processConvertGroup(retargeted, result);
            }
        }
    }

    /**
     * Puts a group conversion into the map, recording a duplicate if the from-group already exists
     * with a different to-group. Identical from/to pairs are silently deduplicated (can occur when
     * Jandex returns repeatable annotations both individually and via their container).
     */
    private void putGroupConversion(Map<DotName, DotName> conversions, DotName fromGroup,
            DotName toGroup, DotName declaringClass, ScanResult result) {
        DotName existingTo = conversions.get(fromGroup);
        if (existingTo != null && !existingTo.equals(toGroup)) {
            result.addConvertGroupDuplicateBean(declaringClass.toString());
            result.addConvertGroupDuplicateError(declaringClass.toString(),
                    "Multiple @ConvertGroup annotations with the same 'from' group "
                            + fromGroup + " on " + declaringClass);
        }
        conversions.put(fromGroup, toGroup);
    }

    private void processConvertGroup(AnnotationInstance annotation, ScanResult result) {
        AnnotationTarget target = annotation.target();
        if (target == null) {
            return;
        }

        AnnotationValue fromValue = annotation.value("from");
        AnnotationValue toValue = annotation.value("to");
        if (fromValue == null || toValue == null) {
            return;
        }
        DotName fromGroup = fromValue.asClass().name();
        DotName toGroup = toValue.asClass().name();

        switch (target.kind()) {
            case FIELD: {
                FieldInfo field = target.asField();
                DotName declaringClass = field.declaringClass().name();
                ScanResult.ConstrainedField existing = findExistingConstrainedField(
                        result, declaringClass, field.name());
                if (existing == null) {
                    existing = new ScanResult.ConstrainedField(
                            field.name(), field.type(), declaringClass,
                            new ArrayList<>(), false, new ArrayList<>(), new LinkedHashMap<>());
                    result.addConstrainedField(declaringClass, existing);
                }
                putGroupConversion(existing.getGroupConversions(), fromGroup, toGroup,
                        declaringClass, result);
                break;
            }
            case METHOD: {
                MethodInfo method = target.asMethod();
                DotName declaringClass = method.declaringClass().name();
                ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                putGroupConversion(existing.getReturnValueGroupConversions(), fromGroup, toGroup,
                        declaringClass, result);
                break;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = target.asMethodParameter();
                MethodInfo method = paramInfo.method();
                DotName declaringClass = method.declaringClass().name();
                short paramIndex = paramInfo.position();
                String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;

                ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                ScanResult.ConstrainedParameter param = findOrCreateParameter(
                        existingMethod, paramIndex, paramName,
                        method.parameterType(paramIndex));
                putGroupConversion(param.getGroupConversions(), fromGroup, toGroup,
                        declaringClass, result);
                break;
            }
            case TYPE: {
                // @ConvertGroup on a type argument (e.g., List<@Valid @ConvertGroup(...) Address>)
                AnnotationTarget enclosing = target.asType().enclosingTarget();
                if (enclosing == null) {
                    break;
                }
                // Check for nested type argument first
                if (tryHandleNestedGroupConversion(enclosing, target.asType(),
                        fromGroup, toGroup, result)) {
                    break;
                }
                // Handle Jandex quirk: @ConvertGroup on param type arg may have METHOD enclosing
                if (enclosing.kind() == AnnotationTarget.Kind.METHOD) {
                    MethodInfo method = enclosing.asMethod();
                    int paramIdx = findParameterContainingType(method, target.asType());
                    if (paramIdx >= 0) {
                        Type paramType = method.parameterType(paramIdx);
                        int paramTypeArgIdx = determineTypeArgumentIndexIn(
                                target.asType().target(), paramType);
                        String paramContainer = paramType.name().toString();
                        addContainerElementGroupConversionOnParam(method, paramIdx,
                                paramTypeArgIdx, paramContainer, fromGroup, toGroup, result);
                        break;
                    }
                }
                int typeArgIdx = determineTypeArgumentIndex(target.asType());
                if (typeArgIdx < 0) {
                    break;
                }
                String containerClass = determineContainerClassName(target.asType());
                addContainerElementGroupConversion(enclosing, typeArgIdx, containerClass,
                        fromGroup, toGroup, result);
                break;
            }
            default:
                break;
        }
    }

    private void addContainerElementGroupConversion(AnnotationTarget enclosing, int typeArgIdx,
            String containerClass, DotName fromGroup, DotName toGroup, ScanResult result) {
        switch (enclosing.kind()) {
            case FIELD: {
                FieldInfo field = enclosing.asField();
                DotName declaringClass = field.declaringClass().name();
                ScanResult.ConstrainedField existing = findExistingConstrainedField(
                        result, declaringClass, field.name());
                if (existing == null) {
                    existing = new ScanResult.ConstrainedField(
                            field.name(), field.type(), declaringClass,
                            new ArrayList<>(), false, new ArrayList<>(), new LinkedHashMap<>());
                    result.addConstrainedField(declaringClass, existing);
                }
                putGroupConversion(findOrCreateContainerElement(existing.getContainerElementConstraints(),
                        typeArgIdx, containerClass).getGroupConversions(), fromGroup, toGroup,
                        declaringClass, result);
                break;
            }
            case METHOD: {
                MethodInfo method = enclosing.asMethod();
                DotName declaringClass = method.declaringClass().name();
                ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                // Apply to return value container elements
                putGroupConversion(findOrCreateContainerElement(existing.getReturnValueContainerElementConstraints(),
                        typeArgIdx, containerClass).getGroupConversions(), fromGroup, toGroup,
                        declaringClass, result);
                break;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                MethodInfo method = paramInfo.method();
                DotName declaringClass = method.declaringClass().name();
                short paramIndex = paramInfo.position();
                String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;
                ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                ScanResult.ConstrainedParameter param = findOrCreateParameter(
                        existingMethod, paramIndex, paramName,
                        method.parameterType(paramIndex));
                putGroupConversion(findOrCreateContainerElement(param.getContainerElementConstraints(),
                        typeArgIdx, containerClass).getGroupConversions(), fromGroup, toGroup,
                        declaringClass, result);
                break;
            }
            default:
                break;
        }
    }

    private void addContainerElementGroupConversionOnParam(MethodInfo method, int paramIdx,
            int typeArgIdx, String containerClass, DotName fromGroup, DotName toGroup,
            ScanResult result) {
        DotName declaringClass = method.declaringClass().name();
        ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
        String paramName = method.parameterName(paramIdx) != null
                ? method.parameterName(paramIdx)
                : "arg" + paramIdx;
        ScanResult.ConstrainedParameter param = findOrCreateParameter(
                existing, paramIdx, paramName, method.parameterType(paramIdx));
        putGroupConversion(findOrCreateContainerElement(param.getContainerElementConstraints(),
                typeArgIdx, containerClass).getGroupConversions(), fromGroup, toGroup,
                declaringClass, result);
    }

    private boolean tryHandleNestedGroupConversion(AnnotationTarget enclosing,
            TypeTarget typeTarget, DotName fromGroup, DotName toGroup, ScanResult result) {
        Type annotatedType = typeTarget.target();
        Type containerType;
        int methodParamIdx = -1;
        MethodInfo methodEnclosing = null;

        switch (enclosing.kind()) {
            case FIELD:
                containerType = enclosing.asField().type();
                break;
            case METHOD: {
                MethodInfo method = enclosing.asMethod();
                methodEnclosing = method;
                int paramIdx = findParameterContainingType(method, typeTarget);
                if (paramIdx >= 0) {
                    containerType = method.parameterType(paramIdx);
                    methodParamIdx = paramIdx;
                } else {
                    containerType = method.returnType();
                }
                break;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                containerType = paramInfo.method().parameterType(paramInfo.position());
                break;
            }
            default:
                return false;
        }

        if (isDirectTypeArgument(containerType, annotatedType)) {
            return false;
        }

        List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(containerType, annotatedType);
        if (path == null || path.size() < 2) {
            return false;
        }

        // Get the container element list for the enclosing element
        // For METHOD enclosing with a parameter match, use the parameter's container elements
        List<ScanResult.ContainerElementConstraintInfo> ceList;
        if (methodParamIdx >= 0) {
            ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, methodEnclosing);
            String paramName = methodEnclosing.parameterName(methodParamIdx) != null
                    ? methodEnclosing.parameterName(methodParamIdx)
                    : "arg" + methodParamIdx;
            ScanResult.ConstrainedParameter param = findOrCreateParameter(
                    existing, methodParamIdx, paramName,
                    methodEnclosing.parameterType(methodParamIdx));
            ceList = param.getContainerElementConstraints();
        } else {
            ceList = getContainerElementListForCascading(enclosing, result);
        }
        if (ceList == null) {
            return false;
        }

        // Determine declaring class for duplicate detection
        DotName nestedDeclaringClass;
        if (enclosing.kind() == AnnotationTarget.Kind.FIELD) {
            nestedDeclaringClass = enclosing.asField().declaringClass().name();
        } else if (enclosing.kind() == AnnotationTarget.Kind.METHOD) {
            nestedDeclaringClass = enclosing.asMethod().declaringClass().name();
        } else if (enclosing.kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
            nestedDeclaringClass = enclosing.asMethodParameter().method().declaringClass().name();
        } else {
            nestedDeclaringClass = null;
        }

        // Navigate to the deepest nested element and add the group conversion
        List<ScanResult.ContainerElementConstraintInfo> currentList = ceList;
        for (int i = 0; i < path.size(); i++) {
            TypeArgumentPathEntry entry = path.get(i);
            ScanResult.ContainerElementConstraintInfo node = findOrCreateNestedEntry(
                    currentList, entry.typeArgIndex, entry.containerClassName);
            if (i == path.size() - 1) {
                if (nestedDeclaringClass != null) {
                    putGroupConversion(node.getGroupConversions(), fromGroup, toGroup,
                            nestedDeclaringClass, result);
                } else {
                    node.getGroupConversions().put(fromGroup, toGroup);
                }
            } else {
                currentList = node.getNestedContainerElements();
            }
        }
        return true;
    }

    private ScanResult.ContainerElementConstraintInfo findOrCreateContainerElement(
            List<ScanResult.ContainerElementConstraintInfo> ceList,
            int typeArgIdx, String containerClass) {
        for (ScanResult.ContainerElementConstraintInfo ce : ceList) {
            if (ce.getTypeArgumentIndex() == typeArgIdx) {
                return ce;
            }
        }
        ScanResult.ContainerElementConstraintInfo newCe = new ScanResult.ContainerElementConstraintInfo(
                typeArgIdx, new ArrayList<>(), false, containerClass);
        ceList.add(newCe);
        return newCe;
    }

    // ---------------------------------------------------------------------------
    // Constraint building helpers
    // ---------------------------------------------------------------------------

    private ScanResult.ScannedConstraint buildScannedConstraint(AnnotationInstance annotation,
            DotName constraintDotName) {
        return buildScannedConstraint(annotation, constraintDotName, null);
    }

    private ScanResult.ScannedConstraint buildScannedConstraint(AnnotationInstance annotation,
            DotName constraintDotName, Type validatedType) {
        Map<String, Object> attributes = extractAttributesWithDefaults(annotation, constraintDotName);
        List<String> groups = extractGroups(annotation);
        List<String> payload = extractPayload(annotation);
        String validatorClassName = validatorResolver.resolveValidator(constraintDotName, validatedType);
        boolean reportAsSingle = isReportAsSingleViolation(constraintDotName);
        List<ScanResult.ScannedConstraint> composing = resolveComposingConstraints(constraintDotName);
        Map<String, Map<String, String>> overrides = extractOverridesAttributes(constraintDotName);
        List<String> allValidators = validatorResolver.resolveAllValidators(constraintDotName);
        Map<String, String> attributeTypes = extractAttributeTypes(constraintDotName);

        return new ScanResult.ScannedConstraint(constraintDotName, attributes, groups, payload,
                validatorClassName, reportAsSingle, composing, overrides, allValidators, attributeTypes);
    }

    /**
     * Extracts annotation attribute values including default values from the annotation definition.
     * This ensures that attributes not explicitly set on the annotation instance still appear
     * in the map with their default values, which is critical for @OverridesAttribute processing.
     */
    private Map<String, Object> extractAttributesWithDefaults(AnnotationInstance annotation,
            DotName constraintDotName) {
        Map<String, Object> attrs = new LinkedHashMap<>(extractAttributes(annotation));

        // Look up the annotation class in the index to find default values
        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass != null) {
            for (MethodInfo method : annotationClass.methods()) {
                String methodName = method.name();
                if (!attrs.containsKey(methodName) && method.defaultValue() != null) {
                    attrs.put(methodName, convertAnnotationValue(method.defaultValue()));
                }
            }
        } else {
            LOG.debugv("Constraint annotation not found in Jandex index for default extraction: {0}",
                    constraintDotName);
        }

        return attrs;
    }

    /**
     * Extracts attribute type categories from the annotation class definition via Jandex.
     * Returns a map of attribute name to type category string, used at runtime
     * to validate @OverridesAttribute type compatibility without reflection.
     */
    private Map<String, String> extractAttributeTypes(DotName constraintDotName) {
        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass == null) {
            return Collections.emptyMap();
        }
        Map<String, String> types = new LinkedHashMap<>();
        for (MethodInfo method : annotationClass.methods()) {
            if (method.parametersCount() == 0 && method.returnType().kind() != Type.Kind.VOID) {
                types.put(method.name(), typeToCategory(method.returnType()));
            }
        }
        return types;
    }

    private String typeToCategory(Type type) {
        switch (type.kind()) {
            case PRIMITIVE:
                return type.asPrimitiveType().primitive().name().toLowerCase();
            case ARRAY:
                Type component = type.asArrayType().componentType();
                if (component.name().equals(DotName.createSimple("java.lang.Class"))) {
                    return "Class[]";
                }
                return "array";
            case CLASS:
                String name = type.name().toString();
                return switch (name) {
                    case "java.lang.String" -> "String";
                    case "java.lang.Class" -> "Class";
                    case "java.lang.Integer", "java.lang.Long", "java.lang.Short",
                            "java.lang.Byte", "java.lang.Float", "java.lang.Double" ->
                        "number";
                    case "java.lang.Boolean" -> "boolean";
                    case "java.lang.Character" -> "char";
                    default -> {
                        // Check if it's an enum via the index
                        ClassInfo cls = this.index.getClassByName(type.name());
                        if (cls != null && cls.isEnum()) {
                            yield "enum";
                        }
                        yield "other";
                    }
                };
            default:
                return "other";
        }
    }

    /**
     * Extracts all annotation attribute values into a {@code Map<String, Object>}.
     * <p>
     * The Jandex {@link AnnotationValue} is converted to a plain Java value:
     * <ul>
     * <li>{@code STRING} &rarr; {@code String}</li>
     * <li>{@code BOOLEAN} &rarr; {@code Boolean}</li>
     * <li>{@code BYTE} &rarr; {@code Byte}</li>
     * <li>{@code SHORT} &rarr; {@code Short}</li>
     * <li>{@code INTEGER} &rarr; {@code Integer}</li>
     * <li>{@code LONG} &rarr; {@code Long}</li>
     * <li>{@code FLOAT} &rarr; {@code Float}</li>
     * <li>{@code DOUBLE} &rarr; {@code Double}</li>
     * <li>{@code CHARACTER} &rarr; {@code Character}</li>
     * <li>{@code CLASS} &rarr; FQCN as {@code String}</li>
     * <li>{@code ENUM} &rarr; enum constant name as {@code String}</li>
     * <li>{@code ARRAY} &rarr; {@code List<Object>}</li>
     * <li>{@code NESTED} &rarr; {@code Map<String, Object>} (recursive)</li>
     * </ul>
     */
    private Map<String, Object> extractAttributes(AnnotationInstance annotation) {
        List<AnnotationValue> values = annotation.values();
        if (values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> attrs = new LinkedHashMap<>(values.size());
        for (AnnotationValue av : values) {
            attrs.put(av.name(), convertAnnotationValue(av));
        }
        return attrs;
    }

    private Object convertAnnotationValue(AnnotationValue av) {
        return switch (av.kind()) {
            case BOOLEAN -> av.asBoolean();
            case BYTE -> av.asByte();
            case SHORT -> av.asShort();
            case INTEGER -> av.asInt();
            case LONG -> av.asLong();
            case FLOAT -> av.asFloat();
            case DOUBLE -> av.asDouble();
            case CHARACTER -> av.asChar();
            case CLASS -> av.asClass().name().toString();
            case ENUM -> av.asEnum();
            case NESTED -> extractAttributes(av.asNested());
            case ARRAY -> convertArrayAnnotationValue(av);
            default -> av.asString();
        };
    }

    private List<Object> convertArrayAnnotationValue(AnnotationValue av) {
        // Determine the component kind by trying each array accessor.
        // Jandex does not expose a "component kind" directly, so we try
        // the most common ones and fall back.

        // Try class array first (common for groups/payload)
        try {
            Type[] classArray = av.asClassArray();
            List<Object> list = new ArrayList<>(classArray.length);
            for (Type t : classArray) {
                list.add(t.name().toString());
            }
            return list;
        } catch (IllegalArgumentException ignored) {
            // not a class array
        }

        // Try string array
        try {
            String[] stringArray = av.asStringArray();
            List<Object> list = new ArrayList<>(stringArray.length);
            Collections.addAll(list, stringArray);
            return list;
        } catch (IllegalArgumentException ignored) {
            // not a string array
        }

        // Try int array
        try {
            int[] intArray = av.asIntArray();
            List<Object> list = new ArrayList<>(intArray.length);
            for (int v : intArray) {
                list.add(v);
            }
            return list;
        } catch (IllegalArgumentException ignored) {
            // not an int array
        }

        // Try long array
        try {
            long[] longArray = av.asLongArray();
            List<Object> list = new ArrayList<>(longArray.length);
            for (long v : longArray) {
                list.add(v);
            }
            return list;
        } catch (IllegalArgumentException ignored) {
            // not a long array
        }

        // Try boolean array
        try {
            boolean[] boolArray = av.asBooleanArray();
            List<Object> list = new ArrayList<>(boolArray.length);
            for (boolean v : boolArray) {
                list.add(v);
            }
            return list;
        } catch (IllegalArgumentException ignored) {
            // not a boolean array
        }

        // Try enum array
        try {
            String[] enumArray = av.asEnumArray();
            List<Object> list = new ArrayList<>(enumArray.length);
            Collections.addAll(list, enumArray);
            return list;
        } catch (IllegalArgumentException ignored) {
            // not an enum array
        }

        // Try nested annotation array
        try {
            AnnotationInstance[] nestedArray = av.asNestedArray();
            List<Object> list = new ArrayList<>(nestedArray.length);
            for (AnnotationInstance nested : nestedArray) {
                list.add(extractAttributes(nested));
            }
            return list;
        } catch (IllegalArgumentException ignored) {
            // not a nested array
        }

        // Fallback: return empty list
        return Collections.emptyList();
    }

    private List<String> extractGroups(AnnotationInstance annotation) {
        AnnotationValue groupsValue = annotation.value("groups");
        if (groupsValue == null) {
            return Collections.emptyList();
        }
        Type[] groupTypes = groupsValue.asClassArray();
        if (groupTypes.length == 0) {
            return Collections.emptyList();
        }
        List<String> groups = new ArrayList<>(groupTypes.length);
        for (Type t : groupTypes) {
            groups.add(t.name().toString());
        }
        return groups;
    }

    private List<String> extractPayload(AnnotationInstance annotation) {
        AnnotationValue payloadValue = annotation.value("payload");
        if (payloadValue == null) {
            return Collections.emptyList();
        }
        Type[] payloadTypes = payloadValue.asClassArray();
        if (payloadTypes.length == 0) {
            return Collections.emptyList();
        }
        List<String> payload = new ArrayList<>(payloadTypes.length);
        for (Type t : payloadTypes) {
            payload.add(t.name().toString());
        }
        return payload;
    }

    private boolean isReportAsSingleViolation(DotName constraintDotName) {
        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass == null) {
            return false;
        }
        return annotationClass.annotation(DotNames.REPORT_AS_SINGLE_VIOLATION) != null;
    }

    /**
     * Resolves composing constraints: constraint annotations that are themselves
     * placed on a custom constraint annotation class.
     */
    private List<ScanResult.ScannedConstraint> resolveComposingConstraints(DotName constraintDotName) {
        return resolveComposingConstraints(constraintDotName, new HashSet<>());
    }

    private List<ScanResult.ScannedConstraint> resolveComposingConstraints(DotName constraintDotName,
            Set<DotName> visited) {
        if (!visited.add(constraintDotName)) {
            return Collections.emptyList(); // prevent infinite recursion
        }

        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass == null) {
            LOG.debugv("Constraint annotation not found in Jandex index for composing resolution: {0}",
                    constraintDotName);
            return Collections.emptyList();
        }

        List<ScanResult.ScannedConstraint> composing = new ArrayList<>();

        for (AnnotationInstance ann : annotationClass.annotations()) {
            DotName annName = ann.name();
            if (DotNames.BUILT_IN_CONSTRAINTS.contains(annName) || isConstraintAnnotation(annName)) {
                // Recursively resolve composing constraints of this composing constraint
                List<ScanResult.ScannedConstraint> nested = resolveComposingConstraints(annName, visited);
                composing.add(buildScannedConstraintWithComposing(ann, annName, nested));
            }
        }

        // Also check for repeatable container annotations on the constraint annotation class
        for (AnnotationInstance ann : annotationClass.annotations()) {
            DotName annName = ann.name();
            // Check if this is a repeatable container for a constraint
            if (isRepeatableContainerForConstraint(annName)) {
                AnnotationValue valueAttr = ann.value();
                if (valueAttr != null) {
                    AnnotationInstance[] nested = valueAttr.asNestedArray();
                    for (AnnotationInstance nestedAnn : nested) {
                        DotName nestedName = nestedAnn.name();
                        List<ScanResult.ScannedConstraint> nestedComposing = resolveComposingConstraints(nestedName,
                                visited);
                        composing.add(buildScannedConstraintWithComposing(nestedAnn, nestedName, nestedComposing));
                    }
                }
            }
        }

        return composing;
    }

    /**
     * Checks if the given annotation name is a repeatable container for a constraint annotation.
     * Handles both top-level names (e.g., "com.example.MyConstraint.List") and inner class names
     * (e.g., "jakarta.validation.constraints.Pattern$List").
     */
    private boolean isRepeatableContainerForConstraint(DotName annotationName) {
        ClassInfo cls = index.getClassByName(annotationName);
        if (cls == null) {
            return false;
        }
        String name = annotationName.toString();
        // Check for inner class names (e.g., "Pattern$List") — Jandex uses '$' for nested classes
        int dollarPos = name.lastIndexOf('$');
        if (dollarPos > 0) {
            String parentName = name.substring(0, dollarPos);
            DotName parentDotName = DotName.createSimple(parentName);
            if (DotNames.BUILT_IN_CONSTRAINTS.contains(parentDotName) || isConstraintAnnotation(parentDotName)) {
                return true;
            }
        }
        // Also check for dot-separated names (e.g., "MyConstraint.List" encoded differently)
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            String parentName = name.substring(0, lastDot);
            DotName parentDotName = DotName.createSimple(parentName);
            if (DotNames.BUILT_IN_CONSTRAINTS.contains(parentDotName) || isConstraintAnnotation(parentDotName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConstraintAnnotation(DotName annotationName) {
        ClassInfo cls = index.getClassByName(annotationName);
        if (cls == null) {
            return false;
        }
        return cls.annotation(DotNames.CONSTRAINT) != null;
    }

    /**
     * Builds a ScannedConstraint without recursing into composing constraints
     * (used when resolving composing constraints to avoid infinite recursion).
     */
    private ScanResult.ScannedConstraint buildScannedConstraintSimple(AnnotationInstance annotation,
            DotName constraintDotName) {
        return buildScannedConstraintWithComposing(annotation, constraintDotName, Collections.emptyList());
    }

    private ScanResult.ScannedConstraint buildScannedConstraintWithComposing(AnnotationInstance annotation,
            DotName constraintDotName, List<ScanResult.ScannedConstraint> composing) {
        Map<String, Object> attributes = extractAttributesWithDefaults(annotation, constraintDotName);
        List<String> groups = extractGroups(annotation);
        List<String> payload = extractPayload(annotation);
        String validatorClassName = validatorResolver.resolveValidator(constraintDotName, null);
        boolean reportAsSingle = isReportAsSingleViolation(constraintDotName);
        Map<String, String> attributeTypes = extractAttributeTypes(constraintDotName);

        return new ScanResult.ScannedConstraint(constraintDotName, attributes, groups, payload,
                validatorClassName, reportAsSingle, composing, Collections.emptyMap(),
                Collections.emptyList(), attributeTypes);
    }

    // ---------------------------------------------------------------------------
    // 8. Build-time constraint definition validation
    // ---------------------------------------------------------------------------

    private void validateConstraintDefinitions(Set<DotName> allConstraints, ScanResult result) {
        for (DotName constraintDotName : allConstraints) {
            validateConstraintDefinition(constraintDotName, result);
        }
    }

    private void validateConstraintDefinition(DotName constraintDotName, ScanResult result) {
        String annotationClassName = constraintDotName.toString();

        // Built-in constraints are known-good, no validation needed
        if (DotNames.BUILT_IN_CONSTRAINTS.contains(constraintDotName)) {
            result.addValidatedConstraintDefinition(annotationClassName);
            return;
        }

        ClassInfo classInfo = index.getClassByName(constraintDotName);
        if (classInfo == null) {
            // All constraint annotations must be in the Jandex index
            LOG.warnv("Constraint annotation {0} is not in the Jandex index and cannot be validated", annotationClassName);
            result.addValidatedConstraintDefinition(annotationClassName);
            return;
        }

        // Validate the constraint definition structure. If any check fails,
        // record the error so it can be thrown at runtime per BV spec.
        try {
            validateConstraintDefinitionStructure(classInfo, annotationClassName);
            validateCrossParameterConstraintRulesJandex(classInfo, annotationClassName);
            validateComposingConstraintTargetsJandex(classInfo, annotationClassName);
        } catch (RuntimeException e) {
            result.addInvalidConstraintDefinition(annotationClassName, e.getMessage());
            return;
        }
        try {
            validateNoMixedDirectAndListComposingJandex(classInfo, annotationClassName);
        } catch (RuntimeException e) {
            // This is a ConstraintDeclarationException, not ConstraintDefinitionException
            result.addInvalidConstraintDeclaration(annotationClassName, e.getMessage());
            return;
        }

        result.addValidatedConstraintDefinition(annotationClassName);
    }

    private void validateConstraintDefinitionStructure(ClassInfo classInfo, String annotationClassName) {
        Map<String, MethodInfo> methodMap = new LinkedHashMap<>();
        for (MethodInfo method : classInfo.methods()) {
            methodMap.put(method.name(), method);
        }

        // Must have message() of type String
        MethodInfo messageMethod = methodMap.get("message");
        if (messageMethod == null || !messageMethod.returnType().name()
                .equals(DotName.createSimple("java.lang.String"))) {
            throw new RuntimeException("missing or invalid message()");
        }

        // Must have groups() of type Class<?>[]
        MethodInfo groupsMethod = methodMap.get("groups");
        if (groupsMethod == null) {
            throw new RuntimeException("missing groups()");
        }
        Type groupsReturnType = groupsMethod.returnType();
        if (groupsReturnType.kind() != Type.Kind.ARRAY
                || !groupsReturnType.asArrayType().constituent().name()
                        .equals(DotName.createSimple("java.lang.Class"))) {
            throw new RuntimeException("invalid groups() return type");
        }
        AnnotationValue groupsDefault = groupsMethod.defaultValue();
        if (groupsDefault == null || groupsDefault.asClassArray().length != 0) {
            throw new RuntimeException("groups() must default to empty array");
        }

        // Must have payload() of type Class<? extends Payload>[]
        MethodInfo payloadMethod = methodMap.get("payload");
        if (payloadMethod == null) {
            throw new RuntimeException("missing payload()");
        }
        Type payloadReturnType = payloadMethod.returnType();
        if (payloadReturnType.kind() != Type.Kind.ARRAY
                || !payloadReturnType.asArrayType().constituent().name()
                        .equals(DotName.createSimple("java.lang.Class"))) {
            throw new RuntimeException("invalid payload() return type");
        }
        AnnotationValue payloadDefault = payloadMethod.defaultValue();
        if (payloadDefault == null || payloadDefault.asClassArray().length != 0) {
            throw new RuntimeException("payload() must default to empty array");
        }

        // No attribute name may start with "valid" (except validationAppliesTo)
        for (String name : methodMap.keySet()) {
            if (name.startsWith("valid") && !"validationAppliesTo".equals(name)) {
                throw new RuntimeException("invalid attribute name: " + name);
            }
        }
    }

    /**
     * Jandex-based port of the runtime {@code validateCrossParameterConstraintRules()}.
     * Validates cross-parameter constraint rules per BV spec.
     */
    private void validateCrossParameterConstraintRulesJandex(ClassInfo classInfo, String annotationClassName) {
        AnnotationInstance constraintMeta = classInfo.annotation(DotNames.CONSTRAINT);
        if (constraintMeta == null) {
            return;
        }
        AnnotationValue validatedBy = constraintMeta.value("validatedBy");
        if (validatedBy == null) {
            return;
        }
        Type[] validators = validatedBy.asClassArray();
        if (validators.length == 0) {
            return;
        }

        boolean hasGenericValidator = false;
        boolean hasCrossParameterValidator = false;
        int crossParameterValidatorCount = 0;

        for (Type validatorType : validators) {
            ClassInfo validatorClass = index.getClassByName(validatorType.name());
            if (validatorClass == null) {
                hasGenericValidator = true;
                continue;
            }

            boolean targetParams = false;
            boolean targetElement = false;

            AnnotationInstance svtAnn = validatorClass.annotation(DotNames.SUPPORTED_VALIDATION_TARGET);
            if (svtAnn == null) {
                hasGenericValidator = true;
            } else {
                AnnotationValue valueAttr = svtAnn.value();
                if (valueAttr != null) {
                    String[] targets = valueAttr.asEnumArray();
                    for (String target : targets) {
                        if (SVT_PARAMETERS.equals(target)) {
                            targetParams = true;
                        }
                        if (SVT_ANNOTATED_ELEMENT.equals(target)) {
                            targetElement = true;
                        }
                    }
                }
                if (targetParams) {
                    hasCrossParameterValidator = true;
                    crossParameterValidatorCount++;

                    // Cross-parameter validators must validate Object or Object[]
                    for (Type iface : validatorClass.interfaceTypes()) {
                        if (iface.kind() == Type.Kind.PARAMETERIZED_TYPE
                                && iface.name().toString().equals("jakarta.validation.ConstraintValidator")) {
                            List<Type> typeArgs = iface.asParameterizedType().arguments();
                            if (typeArgs.size() == 2) {
                                String targetTypeName = typeArgs.get(1).name().toString();
                                if (!"java.lang.Object".equals(targetTypeName)
                                        && !"[Ljava.lang.Object;".equals(targetTypeName)) {
                                    throw new RuntimeException(
                                            "Cross-parameter validator " + validatorType.name()
                                                    + " must validate Object or Object[]");
                                }
                            }
                        }
                    }
                }
                if (targetElement) {
                    hasGenericValidator = true;
                }
            }
        }

        if (crossParameterValidatorCount > 1) {
            throw new RuntimeException(
                    "Constraint " + annotationClassName
                            + " must not have more than one cross-parameter validator");
        }

        Map<String, MethodInfo> methodMap = new LinkedHashMap<>();
        for (MethodInfo method : classInfo.methods()) {
            methodMap.put(method.name(), method);
        }
        MethodInfo validationAppliesToMethod = methodMap.get("validationAppliesTo");

        if (hasGenericValidator && hasCrossParameterValidator) {
            if (validationAppliesToMethod == null) {
                throw new RuntimeException(
                        "Constraint " + annotationClassName
                                + " is both generic and cross-parameter but does not define 'validationAppliesTo'");
            }
        }

        if (hasGenericValidator && !hasCrossParameterValidator && validationAppliesToMethod != null) {
            throw new RuntimeException(
                    "Purely generic constraint " + annotationClassName
                            + " must not define 'validationAppliesTo'");
        }

        if (!hasGenericValidator && hasCrossParameterValidator && validationAppliesToMethod != null) {
            throw new RuntimeException(
                    "Purely cross-parameter constraint " + annotationClassName
                            + " must not define 'validationAppliesTo'");
        }

        if (validationAppliesToMethod != null) {
            String vatTypeName = validationAppliesToMethod.returnType().name().toString();
            if (!"jakarta.validation.ConstraintTarget".equals(vatTypeName)) {
                throw new RuntimeException(
                        "Constraint " + annotationClassName
                                + " 'validationAppliesTo' must be of type ConstraintTarget");
            }
            AnnotationValue vatDefault = validationAppliesToMethod.defaultValue();
            if (vatDefault == null || !ConstraintTarget.IMPLICIT.name().equals(vatDefault.asEnum())) {
                throw new RuntimeException(
                        "Constraint " + annotationClassName
                                + " 'validationAppliesTo' must default to ConstraintTarget.IMPLICIT");
            }
        }
    }

    private Set<String> getValidationTargetsJandex(DotName constraintDotName) {
        Set<String> targets = new HashSet<>();
        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass == null) {
            targets.add(SVT_ANNOTATED_ELEMENT);
            return targets;
        }

        AnnotationInstance constraintMeta = annotationClass.annotation(DotNames.CONSTRAINT);
        if (constraintMeta == null) {
            targets.add(SVT_ANNOTATED_ELEMENT);
            return targets;
        }

        AnnotationValue validatedBy = constraintMeta.value("validatedBy");
        if (validatedBy == null) {
            targets.add(SVT_ANNOTATED_ELEMENT);
            return targets;
        }

        Type[] validators = validatedBy.asClassArray();
        if (validators.length == 0) {
            targets.add(SVT_ANNOTATED_ELEMENT);
            return targets;
        }

        for (Type validatorType : validators) {
            ClassInfo validatorClass = index.getClassByName(validatorType.name());
            if (validatorClass == null) {
                targets.add(SVT_ANNOTATED_ELEMENT);
                continue;
            }

            AnnotationInstance svtAnn = validatorClass.annotation(DotNames.SUPPORTED_VALIDATION_TARGET);
            if (svtAnn == null) {
                targets.add(SVT_ANNOTATED_ELEMENT);
            } else {
                AnnotationValue valueAttr = svtAnn.value();
                if (valueAttr != null) {
                    for (String t : valueAttr.asEnumArray()) {
                        targets.add(t);
                    }
                }
            }
        }
        return targets;
    }

    /**
     * Jandex-based port of the runtime {@code validateComposingConstraintTargets()}.
     * Validates that composing constraints do not have mixed validation targets (BV spec 3.3).
     */
    private void validateComposingConstraintTargetsJandex(ClassInfo classInfo, String annotationClassName) {
        boolean hasParametersTarget = false;
        boolean hasAnnotatedElementTarget = false;

        // Check composing constraint annotations on this constraint
        for (AnnotationInstance ann : classInfo.annotations()) {
            DotName annName = ann.name();
            if (!isConstraintAnnotation(annName) && !DotNames.BUILT_IN_CONSTRAINTS.contains(annName)) {
                continue;
            }

            Set<String> targets = getValidationTargetsJandex(annName);
            if (targets.contains(SVT_PARAMETERS) && !targets.contains(SVT_ANNOTATED_ELEMENT)) {
                hasParametersTarget = true;
            }
            if (targets.contains(SVT_ANNOTATED_ELEMENT) && !targets.contains(SVT_PARAMETERS)) {
                hasAnnotatedElementTarget = true;
            }
        }

        // Also check the constraint's own validators
        Set<String> ownTargets = getValidationTargetsJandex(classInfo.name());
        if (ownTargets.contains(SVT_PARAMETERS) && !ownTargets.contains(SVT_ANNOTATED_ELEMENT)) {
            hasParametersTarget = true;
        }
        if (ownTargets.contains(SVT_ANNOTATED_ELEMENT) && !ownTargets.contains(SVT_PARAMETERS)) {
            hasAnnotatedElementTarget = true;
        }

        if (hasParametersTarget && hasAnnotatedElementTarget) {
            throw new RuntimeException(
                    "Constraint " + annotationClassName
                            + " has composing constraints with incompatible validation targets "
                            + "(mixed PARAMETERS and ANNOTATED_ELEMENT)");
        }
    }

    /**
     * Jandex-based port of the runtime {@code validateNoMixedDirectAndListComposing()}.
     * Validates that @OverridesAttribute with constraintIndex is not used when
     * composing constraints mix direct annotations and List containers (BV spec 3.3).
     */
    private void validateNoMixedDirectAndListComposingJandex(ClassInfo classInfo, String annotationClassName) {
        // Find constraint types where @OverridesAttribute uses constraintIndex >= 0
        Set<DotName> constraintTypesWithIndex = new HashSet<>();

        for (MethodInfo method : classInfo.methods()) {
            for (AnnotationInstance ann : method.annotations()) {
                if (ann.name().equals(DotNames.OVERRIDES_ATTRIBUTE)) {
                    addConstraintTypeIfIndexedJandex(ann, constraintTypesWithIndex);
                } else if (ann.name().equals(DotNames.OVERRIDES_ATTRIBUTE_LIST)) {
                    AnnotationValue valueAttr = ann.value();
                    if (valueAttr != null) {
                        for (AnnotationInstance nested : valueAttr.asNestedArray()) {
                            addConstraintTypeIfIndexedJandex(nested, constraintTypesWithIndex);
                        }
                    }
                }
            }
        }

        if (constraintTypesWithIndex.isEmpty()) {
            return;
        }

        // Check if any of these constraint types appear as both direct and List container
        for (DotName constraintType : constraintTypesWithIndex) {
            DotName containerType = resolveRepeatableContainer(constraintType);
            if (containerType == null) {
                continue;
            }
            boolean hasDirect = classInfo.annotation(constraintType) != null;
            boolean hasContainer = classInfo.annotation(containerType) != null;
            if (hasDirect && hasContainer) {
                throw new RuntimeException(
                        "Constraint " + annotationClassName
                                + " has composing constraint " + constraintType
                                + " specified as both a direct annotation and within a List container. "
                                + "Usage of constraintIndex with mixed direct and List composing constraints "
                                + "is not allowed.");
            }
        }
    }

    private void addConstraintTypeIfIndexedJandex(AnnotationInstance overridesAttr,
            Set<DotName> constraintTypesWithIndex) {
        AnnotationValue constraintValue = overridesAttr.value("constraint");
        AnnotationValue indexValue = overridesAttr.value("constraintIndex");
        if (constraintValue == null || indexValue == null) {
            return;
        }
        int constraintIndex = indexValue.asInt();
        if (constraintIndex >= 0) {
            constraintTypesWithIndex.add(constraintValue.asClass().name());
        }
    }

    // ---------------------------------------------------------------------------
    // 9. Interface group sequence scanning
    // ---------------------------------------------------------------------------

    private void scanInterfaceGroupSequences(ScanResult result) {
        for (AnnotationInstance gsAnnotation : index.getAnnotations(DotNames.GROUP_SEQUENCE)) {
            if (gsAnnotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo classInfo = gsAnnotation.target().asClass();
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) {
                continue; // Only interfaces for interface group sequences
            }

            AnnotationValue value = gsAnnotation.value();
            if (value == null) {
                continue;
            }
            Type[] groupTypes = value.asClassArray();
            List<String> sequence = new ArrayList<>(groupTypes.length);
            for (Type t : groupTypes) {
                sequence.add(t.name().toString());
            }
            result.addInterfaceGroupSequence(classInfo.name().toString(), sequence);
        }
    }

    // ---------------------------------------------------------------------------
    // 10. Cross-parameter validator discovery
    // ---------------------------------------------------------------------------

    private void scanCrossParameterValidators(ScanResult result) {
        for (AnnotationInstance svtAnnotation : index.getAnnotations(DotNames.SUPPORTED_VALIDATION_TARGET)) {
            if (svtAnnotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            AnnotationValue value = svtAnnotation.value();
            if (value == null) {
                continue;
            }
            String[] targets = value.asEnumArray();
            for (String target : targets) {
                if (SVT_PARAMETERS.equals(target)) {
                    result.addCrossParameterValidatorClassName(
                            svtAnnotation.target().asClass().name().toString());
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 11. Validator target type discovery
    // ---------------------------------------------------------------------------

    private static final DotName CONSTRAINT_VALIDATOR = DotName.createSimple("jakarta.validation.ConstraintValidator");

    private void scanValidatorTargetTypes(Set<DotName> allConstraints, ScanResult result) {
        for (DotName constraintDotName : allConstraints) {
            if (DotNames.BUILT_IN_CONSTRAINTS.contains(constraintDotName)) {
                continue;
            }
            ClassInfo annotationClass = index.getClassByName(constraintDotName);
            if (annotationClass == null) {
                continue;
            }
            AnnotationInstance constraintMeta = annotationClass.annotation(DotNames.CONSTRAINT);
            if (constraintMeta == null) {
                continue;
            }
            AnnotationValue validatedBy = constraintMeta.value("validatedBy");
            if (validatedBy == null) {
                continue;
            }
            for (Type validatorType : validatedBy.asClassArray()) {
                String validatorName = validatorType.name().toString();
                if (result.getValidatorTargetTypes().containsKey(validatorName)) {
                    continue;
                }
                String targetTypeName = extractValidatorTargetType(validatorType.name());
                if (targetTypeName != null) {
                    result.addValidatorTargetType(validatorName, targetTypeName);
                }
            }
        }
    }

    private String extractValidatorTargetType(DotName validatorDotName) {
        ClassInfo validatorClass = index.getClassByName(validatorDotName);
        if (validatorClass == null) {
            return null;
        }
        for (Type iface : validatorClass.interfaceTypes()) {
            if (iface.kind() == Type.Kind.PARAMETERIZED_TYPE
                    && iface.name().equals(CONSTRAINT_VALIDATOR)) {
                List<Type> typeArgs = iface.asParameterizedType().arguments();
                if (typeArgs.size() == 2) {
                    return typeArgs.get(1).name().toString();
                }
            }
        }
        // Check superclass chain
        Type superType = validatorClass.superClassType();
        if (superType != null && !superType.name().equals(DotName.createSimple("java.lang.Object"))) {
            return extractValidatorTargetType(superType.name());
        }
        return null;
    }

    // ---------------------------------------------------------------------------
    // @OverridesAttribute extraction (build-time)
    // ---------------------------------------------------------------------------

    /**
     * Extracts @OverridesAttribute mappings from a composed constraint annotation type.
     * Returns a map: composingAnnotationClassName + ":" + constraintIndex -> Map(targetAttrName -> sourceAttrName).
     */
    private Map<String, Map<String, String>> extractOverridesAttributes(DotName constraintDotName) {
        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass == null) {
            LOG.debugv("Constraint annotation not found in Jandex index for @OverridesAttribute extraction: {0}",
                    constraintDotName);
            return Collections.emptyMap();
        }

        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        for (MethodInfo method : annotationClass.methods()) {
            String sourceAttrName = method.name();

            // Check for @OverridesAttribute on this method
            for (AnnotationInstance ann : method.annotations()) {
                if (ann.name().equals(DotNames.OVERRIDES_ATTRIBUTE)) {
                    processOverridesAttributeJandex(ann, sourceAttrName, result);
                } else if (ann.name().equals(DotNames.OVERRIDES_ATTRIBUTE_LIST)) {
                    AnnotationValue valueAttr = ann.value();
                    if (valueAttr != null) {
                        for (AnnotationInstance nested : valueAttr.asNestedArray()) {
                            processOverridesAttributeJandex(nested, sourceAttrName, result);
                        }
                    }
                }
            }
        }

        return result.isEmpty() ? Collections.emptyMap() : result;
    }

    private void processOverridesAttributeJandex(AnnotationInstance overridesAttr,
            String sourceAttrName, Map<String, Map<String, String>> result) {
        AnnotationValue constraintValue = overridesAttr.value("constraint");
        if (constraintValue == null) {
            return;
        }
        String targetConstraint = constraintValue.asClass().name().toString();

        AnnotationValue nameValue = overridesAttr.value("name");
        String targetName = (nameValue != null && !nameValue.asString().isEmpty())
                ? nameValue.asString()
                : sourceAttrName;

        AnnotationValue indexValue = overridesAttr.value("constraintIndex");
        int constraintIndex = indexValue != null ? indexValue.asInt() : 0;

        String key = targetConstraint + ":" + constraintIndex;
        result.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(targetName, sourceAttrName);
    }

    String extractDefaultMessageTemplate(DotName constraintDotName) {
        ClassInfo annotationClass = index.getClassByName(constraintDotName);
        if (annotationClass != null) {
            MethodInfo messageMethod = annotationClass.method("message");
            if (messageMethod != null && messageMethod.defaultValue() != null) {
                return messageMethod.defaultValue().asString();
            }
        } else {
            LOG.debugv("Constraint annotation not found in Jandex index for message template extraction: {0}",
                    constraintDotName);
        }
        return null;
    }

    // ---------------------------------------------------------------------------
    // Lookup / merge helpers
    // ---------------------------------------------------------------------------

    private ScanResult.ConstrainedField findExistingConstrainedField(ScanResult result,
            DotName declaringClass, String fieldName) {
        List<ScanResult.ConstrainedField> fields = result.getConstrainedFields().get(declaringClass);
        if (fields == null) {
            return null;
        }
        for (ScanResult.ConstrainedField f : fields) {
            if (f.getFieldName().equals(fieldName)) {
                return f;
            }
        }
        return null;
    }

    private ScanResult.ConstrainedMethod findExistingConstrainedMethod(ScanResult result,
            DotName declaringClass, String methodName, List<Type> parameterTypes, boolean isCtor) {
        Map<DotName, List<ScanResult.ConstrainedMethod>> map = isCtor
                ? result.getConstrainedConstructors()
                : result.getConstrainedMethods();
        List<ScanResult.ConstrainedMethod> methods = map.get(declaringClass);
        if (methods == null) {
            return null;
        }
        for (ScanResult.ConstrainedMethod m : methods) {
            if (m.getMethodName().equals(methodName) && parameterTypesMatch(m.getParameterTypes(), parameterTypes)) {
                return m;
            }
        }
        return null;
    }

    private boolean parameterTypesMatch(List<Type> a, List<Type> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).name().equals(b.get(i).name())) {
                return false;
            }
        }
        return true;
    }

    private ScanResult.ConstrainedMethod getOrCreateConstrainedMethod(
            ScanResult result, MethodInfo method) {
        DotName declaringClass = method.declaringClass().name();
        boolean isCtor = INIT_METHOD_NAME.equals(method.name());
        ScanResult.ConstrainedMethod existing = findExistingConstrainedMethod(
                result, declaringClass, method.name(), method.parameterTypes(), isCtor);
        if (existing == null) {
            existing = new ScanResult.ConstrainedMethod(
                    method.name(), declaringClass,
                    buildEmptyParameterList(method),
                    new ArrayList<>(), false,
                    new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>(),
                    isCtor, isGetter(method), method.parameterTypes(),
                    method.returnType());
            if (isCtor) {
                result.addConstrainedConstructor(declaringClass, existing);
            } else {
                result.addConstrainedMethod(declaringClass, existing);
            }
        }
        return existing;
    }

    private ScanResult.ConstrainedField getOrCreateConstrainedField(
            ScanResult result, FieldInfo field) {
        DotName declaringClass = field.declaringClass().name();
        ScanResult.ConstrainedField existing = findExistingConstrainedField(
                result, declaringClass, field.name());
        if (existing == null) {
            existing = new ScanResult.ConstrainedField(
                    field.name(), field.type(), declaringClass,
                    new ArrayList<>(), false, new ArrayList<>(), new LinkedHashMap<>());
            result.addConstrainedField(declaringClass, existing);
        }
        return existing;
    }

    private List<ScanResult.ConstrainedParameter> buildEmptyParameterList(MethodInfo method) {
        int paramCount = method.parametersCount();
        List<ScanResult.ConstrainedParameter> params = new ArrayList<>(paramCount);
        for (int i = 0; i < paramCount; i++) {
            String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
            params.add(new ScanResult.ConstrainedParameter(
                    paramName, i, method.parameterType(i),
                    new ArrayList<>(), false, new ArrayList<>(), new LinkedHashMap<>()));
        }
        return params;
    }

    private ScanResult.ConstrainedParameter findOrCreateParameter(
            ScanResult.ConstrainedMethod method, int paramIndex, String paramName, Type paramType) {
        return findOrCreateParameter(method.getParameters(), paramIndex, paramName, paramType);
    }

    private ScanResult.ConstrainedParameter findOrCreateParameter(
            List<ScanResult.ConstrainedParameter> params, int paramIndex, String paramName, Type paramType) {
        for (ScanResult.ConstrainedParameter p : params) {
            if (p.getIndex() == paramIndex) {
                return p;
            }
        }
        ScanResult.ConstrainedParameter newParam = new ScanResult.ConstrainedParameter(
                paramName, paramIndex, paramType,
                new ArrayList<>(), false, new ArrayList<>(), new LinkedHashMap<>());
        params.add(newParam);
        return newParam;
    }

    /**
     * Adds a constraint to the container element constraint list, merging with
     * an existing entry for the same type argument index if one exists.
     *
     * @param numTypeArgs the number of type arguments on the enclosing parameterized type,
     *        used to detect Jandex type deduplication (e.g., Map&lt;@NotNull String, @NotNull String&gt;
     *        where Jandex reuses the same Type instance for both arguments). Pass -1 if unknown.
     */
    private void addContainerElementConstraint(
            List<ScanResult.ContainerElementConstraintInfo> ceList,
            int typeArgIndex, ScanResult.ScannedConstraint constraint,
            String containerClassName, int numTypeArgs) {
        for (ScanResult.ContainerElementConstraintInfo existing : ceList) {
            if (existing.getTypeArgumentIndex() == typeArgIndex) {
                // Check if this constraint type is already present at this position.
                // If so, this is likely a Jandex type deduplication issue where
                // Map<@NotNull String, @NotNull String> has both annotations resolve
                // to index 0. Move the constraint to the next available position.
                boolean duplicateConstraintType = false;
                for (ScanResult.ScannedConstraint sc : existing.getConstraints()) {
                    if (sc.annotationName().equals(constraint.annotationName())) {
                        duplicateConstraintType = true;
                        break;
                    }
                }
                if (duplicateConstraintType && numTypeArgs > 0 && typeArgIndex < numTypeArgs - 1) {
                    // Try the next type argument position
                    addContainerElementConstraint(ceList, typeArgIndex + 1, constraint,
                            containerClassName, numTypeArgs);
                    return;
                }
                existing.getConstraints().add(constraint);
                return;
            }
        }
        List<ScanResult.ScannedConstraint> constraints = new ArrayList<>();
        constraints.add(constraint);
        ceList.add(new ScanResult.ContainerElementConstraintInfo(
                typeArgIndex, constraints, false, containerClassName));
    }

    private void addContainerElementConstraint(
            List<ScanResult.ContainerElementConstraintInfo> ceList,
            int typeArgIndex, ScanResult.ScannedConstraint constraint,
            String containerClassName) {
        addContainerElementConstraint(ceList, typeArgIndex, constraint, containerClassName, -1);
    }

    // ---------------------------------------------------------------------------
    // Type argument helpers
    // ---------------------------------------------------------------------------

    /**
     * Determines the type argument index for a TYPE-target annotation.
     * <p>
     * For a type like {@code List<@NotNull String>}, Jandex provides positional
     * information through the type tree. This method extracts the type argument
     * position. If the position cannot be determined, returns 0 as a fallback.
     */
    private int determineTypeArgumentIndex(TypeTarget typeTarget) {
        return determineTypeArgumentIndex(typeTarget, null);
    }

    private int determineTypeArgumentIndex(TypeTarget typeTarget, AnnotationInstance annotation) {
        // The TypeTarget wraps the annotated Type and gives us the enclosing element.
        // We compare the annotated type against the enclosing element's parameterized
        // type arguments to find the positional index.
        // Returns -1 if the annotation is on the container type itself (not a type argument).
        AnnotationTarget enclosing = typeTarget.enclosingTarget();
        if (enclosing == null) {
            return -1;
        }

        Type annotatedType = typeTarget.target();
        Type enclosingType = getEnclosingFieldOrReturnType(enclosing);
        if (enclosingType != null && enclosingType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            List<Type> typeArgs = enclosingType.asParameterizedType().arguments();
            // First: use reference identity to find the exact type instance.
            // This is critical for cases like Map<String, @NotBlank String> where
            // both type arguments have the same name but are different instances.
            for (int i = 0; i < typeArgs.size(); i++) {
                if (typeArgs.get(i) == annotatedType) {
                    return i;
                }
            }
            // Second: if we have the annotation instance, search each type argument's
            // annotations for a reference match. This handles the case where
            // the annotated type differs from the type argument instances
            // (e.g., Map<@NotNull String, @NotNull String> where both args are the
            // same class but each has its own annotation instance).
            if (annotation != null) {
                for (int i = 0; i < typeArgs.size(); i++) {
                    for (AnnotationInstance ann : typeArgs.get(i).annotations()) {
                        if (ann == annotation) {
                            return i;
                        }
                    }
                }
            }
            // Fallback: use equals() for cases where the Type instances differ
            for (int i = 0; i < typeArgs.size(); i++) {
                if (typeArgs.get(i).equals(annotatedType)) {
                    return i;
                }
            }
            // The annotated type doesn't match any type argument.
            // Check if it's the container type itself (e.g., @Size on List<String>)
            if (annotatedType.name().equals(enclosingType.name())) {
                return -1; // Annotation is on the container type, not a type argument
            }
        }

        // For FIELD targets with a non-parameterized type (e.g., OptionalInt, int, String),
        // the annotation is on the type itself, not on a type argument.
        // For METHOD targets, the enclosing type is the return type, but the annotation
        // might be on a parameter's type argument, so we fall through to default=0
        // (which gets corrected later by findParameterContainingType).
        if (enclosing.kind() == AnnotationTarget.Kind.FIELD
                && (enclosingType == null || enclosingType.kind() != Type.Kind.PARAMETERIZED_TYPE)) {
            return -1;
        }
        // Default: treat as first type argument (index 0) for unresolvable cases
        return 0;
    }

    private String determineContainerClassName(TypeTarget typeTarget) {
        AnnotationTarget enclosing = typeTarget.enclosingTarget();
        if (enclosing == null) {
            return null;
        }

        Type enclosingType = getEnclosingFieldOrReturnType(enclosing);
        if (enclosingType == null) {
            return null;
        }
        // Primitives and void cannot be container types
        if (enclosingType.kind() == Type.Kind.PRIMITIVE || enclosingType.kind() == Type.Kind.VOID) {
            return null;
        }
        return enclosingType.name().toString();
    }

    private Type getEnclosingFieldOrReturnType(AnnotationTarget enclosing) {
        return switch (enclosing.kind()) {
            case FIELD -> enclosing.asField().type();
            case METHOD -> enclosing.asMethod().returnType();
            case METHOD_PARAMETER -> {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                yield paramInfo.method().parameterType(paramInfo.position());
            }
            default -> null;
        };
    }

    /**
     * Checks if a type argument matches the annotation's target type by comparing
     * names. This is a heuristic for when equals() does not work due to annotation
     * decoration differences.
     */
    private boolean typeAnnotationMatches(Type typeArg, Type annotationTarget) {
        return typeArg.name().equals(annotationTarget.name());
    }

    /**
     * Represents a step in a nested type argument path.
     * E.g. for {@code Map<K, List<@Valid V>>}, the path from Map to V is:
     * [(1, "java.util.Map"), (0, "java.util.List")].
     */
    private static class TypeArgumentPathEntry {
        final int typeArgIndex;
        final String containerClassName;

        TypeArgumentPathEntry(int typeArgIndex, String containerClassName) {
            this.typeArgIndex = typeArgIndex;
            this.containerClassName = containerClassName;
        }
    }

    /**
     * Finds the path through nested parameterized type arguments from rootType to targetType.
     * Uses reference identity (==) to match the target type.
     * Returns null if targetType is not found in the type tree.
     */
    private List<TypeArgumentPathEntry> findNestedTypeArgumentPath(Type rootType, Type targetType) {
        if (rootType == null || rootType.kind() != Type.Kind.PARAMETERIZED_TYPE) {
            return null;
        }
        List<Type> typeArgs = rootType.asParameterizedType().arguments();
        for (int i = 0; i < typeArgs.size(); i++) {
            Type typeArg = typeArgs.get(i);
            if (typeArg == targetType) {
                List<TypeArgumentPathEntry> path = new ArrayList<>();
                path.add(new TypeArgumentPathEntry(i, rootType.name().toString()));
                return path;
            }
            // Recurse into nested parameterized types
            if (typeArg.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                List<TypeArgumentPathEntry> nestedPath = findNestedTypeArgumentPath(typeArg, targetType);
                if (nestedPath != null) {
                    List<TypeArgumentPathEntry> path = new ArrayList<>();
                    path.add(new TypeArgumentPathEntry(i, rootType.name().toString()));
                    path.addAll(nestedPath);
                    return path;
                }
            }
        }
        return null;
    }

    private boolean isDirectTypeArgument(Type enclosingType, Type annotatedType) {
        if (enclosingType == null || enclosingType.kind() != Type.Kind.PARAMETERIZED_TYPE) {
            return false;
        }
        for (Type typeArg : enclosingType.asParameterizedType().arguments()) {
            if (typeArg == annotatedType) {
                return true;
            }
        }
        return false;
    }

    private void addNestedContainerElementConstraint(
            List<ScanResult.ContainerElementConstraintInfo> topCeList,
            List<TypeArgumentPathEntry> path,
            ScanResult.ScannedConstraint constraint) {
        // Navigate/create the chain from the first entry to the second-to-last
        List<ScanResult.ContainerElementConstraintInfo> currentList = topCeList;
        for (int i = 0; i < path.size() - 1; i++) {
            TypeArgumentPathEntry entry = path.get(i);
            ScanResult.ContainerElementConstraintInfo node = findOrCreateNestedEntry(
                    currentList, entry.typeArgIndex, entry.containerClassName);
            currentList = node.getNestedContainerElements();
        }
        // Add the constraint at the deepest level
        TypeArgumentPathEntry lastEntry = path.get(path.size() - 1);
        addContainerElementConstraint(currentList, lastEntry.typeArgIndex, constraint,
                lastEntry.containerClassName);
    }

    private void markNestedContainerElementCascading(
            List<ScanResult.ContainerElementConstraintInfo> topCeList,
            List<TypeArgumentPathEntry> path) {
        List<ScanResult.ContainerElementConstraintInfo> currentList = topCeList;
        for (int i = 0; i < path.size() - 1; i++) {
            TypeArgumentPathEntry entry = path.get(i);
            ScanResult.ContainerElementConstraintInfo node = findOrCreateNestedEntry(
                    currentList, entry.typeArgIndex, entry.containerClassName);
            currentList = node.getNestedContainerElements();
        }
        TypeArgumentPathEntry lastEntry = path.get(path.size() - 1);
        markOrCreateContainerElementCascading(currentList, lastEntry.typeArgIndex,
                lastEntry.containerClassName);
    }

    private ScanResult.ContainerElementConstraintInfo findOrCreateNestedEntry(
            List<ScanResult.ContainerElementConstraintInfo> ceList,
            int typeArgIndex, String containerClassName) {
        for (ScanResult.ContainerElementConstraintInfo existing : ceList) {
            if (existing.getTypeArgumentIndex() == typeArgIndex) {
                return existing;
            }
        }
        ScanResult.ContainerElementConstraintInfo newEntry = new ScanResult.ContainerElementConstraintInfo(
                typeArgIndex, new ArrayList<>(), false, containerClassName);
        ceList.add(newEntry);
        return newEntry;
    }

    /**
     * Checks if the annotated type from a TypeTarget is within a method parameter's
     * type arguments (not the return type). Returns the parameter index if found, -1 otherwise.
     * Checks both direct type arguments and nested parameterized type arguments.
     */
    private int findParameterContainingType(MethodInfo method, TypeTarget typeTarget) {
        Type annotatedType = typeTarget.target();
        // First pass: check direct type arguments (original behavior)
        for (int i = 0; i < method.parametersCount(); i++) {
            Type paramType = method.parameterType(i);
            if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                List<Type> typeArgs = paramType.asParameterizedType().arguments();
                for (Type typeArg : typeArgs) {
                    if (typeArg == annotatedType) {
                        return i;
                    }
                }
            }
        }
        // Second pass: check nested type arguments (for nested container elements)
        for (int i = 0; i < method.parametersCount(); i++) {
            Type paramType = method.parameterType(i);
            if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                for (Type typeArg : paramType.asParameterizedType().arguments()) {
                    if (typeArg.kind() == Type.Kind.PARAMETERIZED_TYPE
                            && containsType(typeArg, annotatedType)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private boolean containsType(Type containerType, Type targetType) {
        if (containerType == targetType) {
            return true;
        }
        if (containerType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            for (Type typeArg : containerType.asParameterizedType().arguments()) {
                if (containsType(typeArg, targetType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handles constraint annotations on nested type arguments.
     * For example, {@code Map<K, List<@NotNull V>>} has @NotNull at a nested position.
     * Returns true if handled as a nested type, false if not nested (caller should handle).
     */
    private boolean tryHandleNestedTypeConstraint(AnnotationTarget enclosing,
            TypeTarget typeTarget, ScanResult.ScannedConstraint constraint, ScanResult result) {
        Type annotatedType = typeTarget.target();

        switch (enclosing.kind()) {
            case FIELD: {
                Type fieldType = enclosing.asField().type();
                if (isDirectTypeArgument(fieldType, annotatedType)) {
                    return false; // Direct type arg, not nested
                }
                List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(fieldType, annotatedType);
                if (path == null || path.size() < 2) {
                    return false;
                }
                ScanResult.ConstrainedField existing = getOrCreateConstrainedField(result, enclosing.asField());
                addNestedContainerElementConstraint(
                        existing.getContainerElementConstraints(), path, constraint);
                return true;
            }
            case METHOD: {
                MethodInfo method = enclosing.asMethod();
                // First check if the type is in a parameter (Jandex reports these as METHOD)
                int paramIdx = findParameterContainingType(method, typeTarget);
                if (paramIdx >= 0) {
                    Type paramType = method.parameterType(paramIdx);
                    if (isDirectTypeArgument(paramType, annotatedType)) {
                        return false; // Direct type arg of parameter
                    }
                    List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(paramType, annotatedType);
                    if (path == null || path.size() < 2) {
                        return false;
                    }
                    ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                    String paramName = method.parameterName(paramIdx) != null
                            ? method.parameterName(paramIdx)
                            : "arg" + paramIdx;
                    ScanResult.ConstrainedParameter param = findOrCreateParameter(
                            existing, paramIdx, paramName, paramType);
                    addNestedContainerElementConstraint(
                            param.getContainerElementConstraints(), path, constraint);
                    return true;
                }
                // Check return type
                Type returnType = method.returnType();
                if (isDirectTypeArgument(returnType, annotatedType)) {
                    return false;
                }
                List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(returnType, annotatedType);
                if (path == null || path.size() < 2) {
                    return false;
                }
                ScanResult.ConstrainedMethod existing = getOrCreateConstrainedMethod(result, method);
                addNestedContainerElementConstraint(
                        existing.getReturnValueContainerElementConstraints(), path, constraint);
                return true;
            }
            case METHOD_PARAMETER: {
                MethodParameterInfo paramInfo = enclosing.asMethodParameter();
                Type paramType = paramInfo.method().parameterType(paramInfo.position());
                if (isDirectTypeArgument(paramType, annotatedType)) {
                    return false;
                }
                List<TypeArgumentPathEntry> path = findNestedTypeArgumentPath(paramType, annotatedType);
                if (path == null || path.size() < 2) {
                    return false;
                }
                MethodInfo method = paramInfo.method();
                short paramIndex = paramInfo.position();
                String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + paramIndex;
                ScanResult.ConstrainedMethod existingMethod = getOrCreateConstrainedMethod(result, method);
                ScanResult.ConstrainedParameter param = findOrCreateParameter(
                        existingMethod, paramIndex, paramName, method.parameterType(paramIndex));
                addNestedContainerElementConstraint(
                        param.getContainerElementConstraints(), path, constraint);
                return true;
            }
            default:
                return false;
        }
    }

    private int determineTypeArgumentIndexIn(Type annotatedType, Type containerType) {
        if (containerType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            List<Type> typeArgs = containerType.asParameterizedType().arguments();
            for (int i = 0; i < typeArgs.size(); i++) {
                if (typeArgs.get(i) == annotatedType) {
                    return i;
                }
            }
            // Fallback: equals comparison
            for (int i = 0; i < typeArgs.size(); i++) {
                if (typeArgs.get(i).equals(annotatedType)) {
                    return i;
                }
            }
        }
        return 0;
    }

    /**
     * Determines whether a method is a JavaBeans-style getter.
     * A getter is a no-arg method whose name starts with "get" (followed by an
     * uppercase letter) or "is" (followed by an uppercase letter) and has a
     * non-void return type.
     */
    private boolean isGetter(MethodInfo method) {
        if (method.parametersCount() != 0) {
            return false;
        }
        if (method.returnType().kind() == Type.Kind.VOID) {
            return false;
        }
        String name = method.name();
        if (name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3))) {
            return true;
        }
        if (name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2))) {
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------------
    // 12. Type hierarchy pre-computation
    // ---------------------------------------------------------------------------

    private void scanTypeHierarchies(ScanResult result) {
        for (DotName className : getAllConstrainedClassesWithSubclasses(result)) {
            List<String> hierarchy = new ArrayList<>();
            Set<DotName> seen = new HashSet<>();
            collectJandexTypeHierarchy(className, hierarchy, seen);
            result.addTypeHierarchy(className, hierarchy);
        }
    }

    /**
     * Returns all constrained classes plus their known subclasses/implementors from the Jandex index.
     * This ensures subclasses that inherit constraints are covered by build-time metadata.
     */
    private Set<DotName> getAllConstrainedClassesWithSubclasses(ScanResult result) {
        Set<DotName> all = new LinkedHashSet<>(result.getAllConstrainedClasses());
        for (DotName className : result.getAllConstrainedClasses()) {
            ClassInfo classInfo = index.getClassByName(className);
            if (classInfo == null) {
                continue;
            }
            for (ClassInfo sub : index.getAllKnownSubclasses(className)) {
                all.add(sub.name());
            }
            if (java.lang.reflect.Modifier.isInterface(classInfo.flags())) {
                for (ClassInfo impl : index.getAllKnownImplementors(className)) {
                    all.add(impl.name());
                }
            }
        }
        return all;
    }

    private void collectJandexTypeHierarchy(DotName className, List<String> result, Set<DotName> seen) {
        if (className == null || !seen.add(className)) {
            return;
        }
        // Exclude java.lang.Object
        if (className.toString().equals("java.lang.Object")) {
            return;
        }
        result.add(className.toString());
        ClassInfo classInfo = index.getClassByName(className);
        if (classInfo != null) {
            collectJandexTypeHierarchy(classInfo.superName(), result, seen);
            for (DotName iface : classInfo.interfaceNames()) {
                collectJandexTypeHierarchy(iface, result, seen);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 13. All property names pre-computation
    // ---------------------------------------------------------------------------

    private void scanAllPropertyNames(ScanResult result) {
        for (DotName className : getAllConstrainedClassesWithSubclasses(result)) {
            Set<String> propertyNames = new LinkedHashSet<>();
            // Walk the type hierarchy to collect ALL field and getter-derived property names
            List<String> hierarchy = result.getTypeHierarchies().get(className);
            if (hierarchy != null) {
                for (String typeName : hierarchy) {
                    ClassInfo classInfo = index.getClassByName(DotName.createSimple(typeName));
                    if (classInfo != null) {
                        collectPropertyNames(classInfo, propertyNames);
                    }
                }
            } else {
                // Fallback: just scan the class itself
                ClassInfo classInfo = index.getClassByName(className);
                if (classInfo != null) {
                    collectPropertyNames(classInfo, propertyNames);
                }
            }
            result.addAllPropertyNames(className, propertyNames);
        }
    }

    private void collectPropertyNames(ClassInfo classInfo, Set<String> propertyNames) {
        for (FieldInfo field : classInfo.fields()) {
            propertyNames.add(field.name());
        }
        for (MethodInfo method : classInfo.methods()) {
            if (method.parametersCount() == 0 && method.returnType().kind() != Type.Kind.VOID) {
                String name = method.name();
                String propertyName = getPropertyNameFromGetter(name);
                if (propertyName != null) {
                    propertyNames.add(propertyName);
                }
            }
        }
    }

    private String getPropertyNameFromGetter(String name) {
        if (name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3))) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2))) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return null;
    }

    // ---------------------------------------------------------------------------
    // 14. Constraint-to-validator mapping
    // ---------------------------------------------------------------------------

    private void scanConstraintValidatorMapping(Set<DotName> allConstraints, ScanResult result) {
        for (DotName constraintDotName : allConstraints) {
            List<String> validators = validatorResolver.resolveAllValidators(constraintDotName);
            if (!validators.isEmpty()) {
                result.addConstraintValidatorMapping(constraintDotName.toString(), validators);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 15. Iterable type argument pre-computation
    // ---------------------------------------------------------------------------

    private void scanIterableTypeArguments(ScanResult result) {
        for (DotName className : getAllConstrainedClassesWithSubclasses(result)) {
            ClassInfo classInfo = index.getClassByName(className);
            if (classInfo == null) {
                continue;
            }
            if (!isIterableClass(classInfo)) {
                continue;
            }
            String elementType = resolveIterableTypeArgument(classInfo);
            if (elementType != null) {
                result.addIterableTypeArgument(className.toString(), elementType);
            }
        }
    }

    private boolean isIterableClass(ClassInfo classInfo) {
        if (classInfo.name().equals(DotNames.ITERABLE)) {
            return true;
        }
        // Check superclass chain
        DotName superName = classInfo.superName();
        if (superName != null) {
            ClassInfo superClass = index.getClassByName(superName);
            if (superClass != null && isIterableClass(superClass)) {
                return true;
            }
        }
        // Check interfaces
        for (DotName iface : classInfo.interfaceNames()) {
            ClassInfo ifaceClass = index.getClassByName(iface);
            if (ifaceClass != null && isIterableClass(ifaceClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the Iterable type argument from a class's generic hierarchy using Jandex.
     * For example, for {@code SubClassHContainer extends ArrayList<SubClassH>},
     * returns {@code "com.example.SubClassH"}.
     */
    private String resolveIterableTypeArgument(ClassInfo classInfo) {
        // Walk superclass chain
        Type superType = classInfo.superClassType();
        while (superType != null) {
            if (superType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                ClassInfo rawClass = index.getClassByName(superType.name());
                if (rawClass != null && isIterableClass(rawClass)) {
                    List<Type> typeArgs = superType.asParameterizedType().arguments();
                    if (!typeArgs.isEmpty() && typeArgs.get(0).kind() == Type.Kind.CLASS) {
                        return typeArgs.get(0).name().toString();
                    }
                }
                ClassInfo nextClass = index.getClassByName(superType.name());
                superType = nextClass != null ? nextClass.superClassType() : null;
            } else if (superType.kind() == Type.Kind.CLASS) {
                ClassInfo nextClass = index.getClassByName(superType.name());
                superType = nextClass != null ? nextClass.superClassType() : null;
            } else {
                break;
            }
        }
        // Check interfaces
        for (Type iface : classInfo.interfaceTypes()) {
            if (iface.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                ClassInfo rawClass = index.getClassByName(iface.name());
                if (rawClass != null && isIterableClass(rawClass)) {
                    List<Type> typeArgs = iface.asParameterizedType().arguments();
                    if (!typeArgs.isEmpty() && typeArgs.get(0).kind() == Type.Kind.CLASS) {
                        return typeArgs.get(0).name().toString();
                    }
                }
            }
        }
        return null;
    }
}
