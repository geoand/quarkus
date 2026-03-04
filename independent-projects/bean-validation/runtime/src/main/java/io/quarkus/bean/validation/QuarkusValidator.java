package io.quarkus.bean.validation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintDefinitionException;
import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupDefinitionException;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.valueextraction.ValueExtractor;

import io.quarkus.bean.validation.impl.PathImpl;
import io.quarkus.bean.validation.impl.QuarkusConstraintValidatorContext;
import io.quarkus.bean.validation.impl.QuarkusConstraintViolation;
import io.quarkus.bean.validation.impl.QuarkusExecutableValidator;
import io.quarkus.bean.validation.impl.ValidationUtils;
import io.quarkus.bean.validation.impl.constraints.BuiltinConstraintValidators;
import io.quarkus.bean.validation.impl.metadata.descriptor.BeanDescriptorImpl;
import io.quarkus.bean.validation.impl.metadata.descriptor.ConstraintDescriptorImpl;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedBeanMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedFieldMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedMethodMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedParameterMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstraintMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ContainerElementConstraint;

public class QuarkusValidator implements Validator {

    private final BeanValidationMetadata metadata;
    private final MessageInterpolator messageInterpolator;
    private final TraversableResolver traversableResolver;
    private final ConstraintValidatorFactory constraintValidatorFactory;
    private final ParameterNameProvider parameterNameProvider;
    private final ClockProvider clockProvider;
    private final QuarkusExecutableValidator executableValidator;
    private final Set<ValueExtractor<?>> customValueExtractors;

    private final Map<String, BeanDescriptorImpl> descriptorCache = new ConcurrentHashMap<>();
    private final Set<String> validatedConstraintDefinitions = ConcurrentHashMap.newKeySet();
    private final Set<String> validatedConstraintTargetBeans = ConcurrentHashMap.newKeySet();
    private final Map<String, Class<?>> validatorTargetTypeCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Class<?>>> typeHierarchyCache = new ConcurrentHashMap<>();
    private final Map<ConstraintMetadata, ConstraintDescriptor<?>> constraintDescriptorCache = new ConcurrentHashMap<>();
    private final Map<ConstraintMetadata, Annotation> annotationProxyCache = new ConcurrentHashMap<>();
    private final Map<ValidatorCacheKey, ConstraintValidator<?, ?>> validatorCache = new ConcurrentHashMap<>();
    private final Map<String, List<Set<Class<?>>>> groupSequenceStepsCache = new ConcurrentHashMap<>();

    public QuarkusValidator(BeanValidationMetadata metadata,
            MessageInterpolator messageInterpolator,
            TraversableResolver traversableResolver,
            ConstraintValidatorFactory constraintValidatorFactory,
            ParameterNameProvider parameterNameProvider,
            ClockProvider clockProvider) {
        this(metadata, messageInterpolator, traversableResolver, constraintValidatorFactory,
                parameterNameProvider, clockProvider, Collections.emptySet());
    }

    public QuarkusValidator(BeanValidationMetadata metadata,
            MessageInterpolator messageInterpolator,
            TraversableResolver traversableResolver,
            ConstraintValidatorFactory constraintValidatorFactory,
            ParameterNameProvider parameterNameProvider,
            ClockProvider clockProvider,
            Set<ValueExtractor<?>> valueExtractors) {
        this.metadata = metadata != null ? metadata
                : new BeanValidationMetadata(Map.of(), Set.of(), Set.of(), Map.of(),
                        Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        this.messageInterpolator = messageInterpolator;
        this.traversableResolver = traversableResolver;
        this.constraintValidatorFactory = constraintValidatorFactory;
        this.parameterNameProvider = parameterNameProvider;
        this.clockProvider = clockProvider;
        this.customValueExtractors = valueExtractors != null ? valueExtractors : Collections.emptySet();
        this.executableValidator = new QuarkusExecutableValidator(this);
        preloadDescriptors();
    }

    /**
     * Pre-populates the descriptor cache for all known constrained beans.
     * This moves Class.forName() and BeanDescriptorImpl.build() work from first-access
     * to construction time.
     */
    private void preloadDescriptors() {
        for (String className : metadata.beans().keySet()) {
            Class<?> clazz = loadClass(className);
            getConstraintsForClass(clazz);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("Validated object must not be null");
        }
        Class<T> beanClass = (Class<T>) object.getClass();

        // Validate group conversion rules on the bean class
        validateGroupConversionsForBean(beanClass);

        // Validate constraint target usage (validationAppliesTo) on bean fields and class
        validateConstraintTargetForBeanValidation(beanClass);

        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();

        // Check if any requested group is a @GroupSequence interface
        // and process sequences with short-circuiting
        List<Set<Class<?>>> groupSequenceSteps = resolveGroupSequenceInterfaces(groupSet);

        if (groupSequenceSteps != null) {
            // Process as a group sequence: stop at first step with violations
            for (Set<Class<?>> step : groupSequenceSteps) {
                Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                int before = violations.size();
                validateBean(object, beanClass, object, beanClass,
                        PathImpl.createRootPath(), step, violations, visited);
                if (violations.size() > before) {
                    break; // Stop at first failing sequence step
                }
            }
        } else {
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            validateBean(object, beanClass, object, beanClass,
                    PathImpl.createRootPath(), groupSet, violations, visited);
        }

        return violations;
    }

    /**
     * Checks if the requested groups contain a @GroupSequence interface.
     * If so, returns the sequence as a list of group sets (each step is a set of groups).
     * If not, returns null indicating no sequence processing needed.
     */
    private List<Set<Class<?>>> resolveGroupSequenceInterfaces(Set<Class<?>> groups) {
        // If we have a single group that is a @GroupSequence, expand it
        for (Class<?> group : groups) {
            if (Default.class.equals(group)) {
                continue; // Default is handled by bean-level @GroupSequence
            }
            Class<?>[] sequence = getGroupSequenceFromInterface(group);
            if (sequence != null) {
                // Validate: Default.class must not appear in @GroupSequence (BV spec 3.4.3)
                for (Class<?> seqGroup : sequence) {
                    if (Default.class.equals(seqGroup)) {
                        throw new GroupDefinitionException(
                                "Default.class must not be part of a @GroupSequence on " + group.getName());
                    }
                }
                // Validate no cycles
                detectGroupSequenceCycle(group, new HashSet<>());

                List<Set<Class<?>>> steps = new ArrayList<>();
                for (Class<?> seqGroup : sequence) {
                    // Each step may need group inheritance expansion
                    Set<Class<?>> expandedStep = expandGroupWithInheritance(seqGroup);
                    steps.add(expandedStep);
                }
                return steps;
            }
        }
        return null;
    }

    private List<Set<Class<?>>> resolveGroupSteps(Class<?> beanClass, Set<Class<?>> groups) {
        List<Set<Class<?>>> seqSteps = resolveGroupSequenceInterfaces(groups);
        if (seqSteps == null) {
            seqSteps = resolveBeanGroupSequence(beanClass, groups);
        }
        return seqSteps != null ? seqSteps : Collections.singletonList(groups);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("Validated object must not be null");
        }
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be null or empty");
        }
        Class<T> beanClass = (Class<T>) object.getClass();

        // Validate property exists on the type (BV spec 5.1.1)
        if (!propertyExistsOnType(beanClass, propertyName)) {
            throw new IllegalArgumentException(
                    "Property '" + propertyName + "' does not exist on " + beanClass.getName());
        }

        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();

        List<Set<Class<?>>> groupSteps = resolveGroupSteps(beanClass, groupSet);

        for (Set<Class<?>> stepGroups : groupSteps) {
            int before = violations.size();
            // Walk the full type hierarchy (classes + interfaces) to find property constraints
            for (Class<?> current : getTypeHierarchy(beanClass)) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
                if (beanMeta != null) {
                    Set<Class<?>> effectiveGroups = current.isInterface()
                            ? augmentWithImplicitGroup(stepGroups, Collections.singleton(current))
                            : stepGroups;

                    for (ConstrainedFieldMetadata fieldMeta : beanMeta.fields()) {
                        if (fieldMeta.name().equals(propertyName)) {
                            PathImpl.PropertyNodeImpl propNode = new PathImpl.PropertyNodeImpl(propertyName);
                            PathImpl rootPath = PathImpl.createRootPath();
                            if (!isReachable(object, propNode, beanClass, rootPath,
                                    ElementType.FIELD)) {
                                continue;
                            }
                            Object value = getFieldValue(object, beanClass, propertyName);
                            PathImpl path = rootPath.append(propNode);
                            validateFieldConstraints(object, beanClass, object, value, fieldMeta, path,
                                    effectiveGroups, violations, null, null, null);
                        }
                    }

                    for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
                        if (methodMeta.getter()
                                && getPropertyNameFromGetter(methodMeta.name()).equals(propertyName)) {
                            PathImpl.PropertyNodeImpl propNode = new PathImpl.PropertyNodeImpl(propertyName);
                            PathImpl rootPath = PathImpl.createRootPath();
                            if (!isReachable(object, propNode, beanClass, rootPath,
                                    ElementType.METHOD)) {
                                continue;
                            }
                            Object value = getPropertyValue(object, current, methodMeta.name());
                            PathImpl path = rootPath.append(propNode);
                            validateReturnValueConstraints(object, beanClass, object, value, methodMeta, path,
                                    effectiveGroups, violations, null, null);
                        }
                    }
                }
            }
            if (groupSteps.size() > 1 && violations.size() > before) {
                break;
            }
        }

        return violations;
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName,
            Object value, Class<?>... groups) {
        if (beanType == null) {
            throw new IllegalArgumentException("Bean type must not be null");
        }
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be null or empty");
        }

        // Validate property exists on the type (BV spec 5.1.1)
        if (!propertyExistsOnType(beanType, propertyName)) {
            throw new IllegalArgumentException(
                    "Property '" + propertyName + "' does not exist on " + beanType.getName());
        }

        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();

        List<Set<Class<?>>> groupSteps = resolveGroupSteps(beanType, groupSet);

        for (Set<Class<?>> stepGroups : groupSteps) {
            int before = violations.size();
            // Walk the full type hierarchy (classes + interfaces) to find property constraints
            for (Class<?> current : getTypeHierarchy(beanType)) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
                if (beanMeta != null) {
                    Set<Class<?>> effectiveGroups = current.isInterface()
                            ? augmentWithImplicitGroup(stepGroups, Collections.singleton(current))
                            : stepGroups;
                    validateValueForBean(beanType, propertyName, value, beanMeta, effectiveGroups, violations);
                }
            }
            if (groupSteps.size() > 1 && violations.size() > before) {
                break;
            }
        }

        return violations;
    }

    private <T> void validateValueForBean(Class<T> beanType, String propertyName, Object value,
            ConstrainedBeanMetadata beanMeta, Set<Class<?>> groupSet, Set<ConstraintViolation<T>> violations) {

        for (ConstrainedFieldMetadata fieldMeta : beanMeta.fields()) {
            if (fieldMeta.name().equals(propertyName)) {
                PathImpl.PropertyNodeImpl propNode = new PathImpl.PropertyNodeImpl(propertyName);
                PathImpl rootPath = PathImpl.createRootPath();
                if (!isReachable(null, propNode, beanType, rootPath, ElementType.FIELD)) {
                    continue;
                }
                PathImpl path = rootPath.append(propNode);
                validateFieldConstraints(null, beanType, null, value, fieldMeta, path, groupSet, violations,
                        null, null, null);
            }
        }

        for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
            if (methodMeta.getter()
                    && getPropertyNameFromGetter(methodMeta.name()).equals(propertyName)) {
                PathImpl.PropertyNodeImpl propNode = new PathImpl.PropertyNodeImpl(propertyName);
                PathImpl rootPath = PathImpl.createRootPath();
                if (!isReachable(null, propNode, beanType, rootPath, ElementType.METHOD)) {
                    continue;
                }
                PathImpl path = rootPath.append(propNode);
                validateReturnValueConstraints(null, beanType, null, value, methodMeta, path, groupSet, violations,
                        null, null);
            }
        }
    }

    @Override
    public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class must not be null");
        }
        return descriptorCache.computeIfAbsent(clazz.getName(), new Function<>() {
            @Override
            public BeanDescriptorImpl apply(String key) {
                // Walk the full type hierarchy and collect all metadata
                List<ConstrainedBeanMetadata> allMeta = new ArrayList<>();
                for (Class<?> c : getTypeHierarchy(clazz)) {
                    ConstrainedBeanMetadata m = metadata.getBean(c.getName());
                    if (m != null) {
                        allMeta.add(m);
                    }
                }
                return BeanDescriptorImpl.build(clazz, allMeta);
            }
        });
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(QuarkusValidator.class)) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap to " + type);
    }

    @Override
    public ExecutableValidator forExecutables() {
        return executableValidator;
    }

    // --- Internal validation methods ---

    <T> void validateBean(Object rootBean, Class<?> rootBeanClass, Object bean, Class<?> beanClass,
            PathImpl currentPath, Set<Class<?>> groups, Set<ConstraintViolation<T>> violations,
            Set<Object> visited) {
        validateBean(rootBean, rootBeanClass, bean, beanClass, currentPath, groups, violations,
                visited, null, null, false, null, null, null, null);
    }

    /**
     * Validates a bean, optionally marking all first-level path nodes with iterable context.
     * When this bean was reached by iterating a container (List, Set, Map, array), the
     * iterable info is applied to each PropertyNode/BeanNode created at this level.
     *
     * @param containerClass the container class (e.g. List.class) that this bean was extracted from, or null
     * @param containerTypeArgIndex the type argument index within the container, or null
     */
    <T> void validateBean(Object rootBean, Class<?> rootBeanClass, Object bean, Class<?> beanClass,
            PathImpl currentPath, Set<Class<?>> groups, Set<ConstraintViolation<T>> violations,
            Set<Object> visited, Integer iterIndex, Object iterKey, boolean inIterable,
            Class<?> containerClass, Integer containerTypeArgIndex,
            Object[] executableParameters, Object executableReturnValue) {

        if (bean == null || !visited.add(bean)) {
            return; // null or already visited on this path (cycle detection)
        }
        try {
            // Per BV spec 5.4: @GroupSequence isolation — if the ACTUAL bean class declares
            // its own @GroupSequence, that sequence applies for ALL constraints in the hierarchy.
            // Parent class sequences are ignored (overridden by the child's sequence).
            // If the bean class does NOT declare @GroupSequence, each hierarchy class may use
            // its own sequence independently.
            List<Class<?>> hierarchy = getTypeHierarchy(beanClass);
            ConstrainedBeanMetadata topBeanMeta = metadata.getBean(beanClass.getName());
            boolean topHasSequence = topBeanMeta != null && topBeanMeta.groupSequence() != null;

            if (topHasSequence) {
                // Bean class has @GroupSequence — use it for ALL hierarchy classes
                validateBeanWithTopLevelSequence(rootBean, rootBeanClass, bean, beanClass,
                        currentPath, groups, violations, visited, iterIndex, iterKey, inIterable,
                        containerClass, containerTypeArgIndex, executableParameters, executableReturnValue,
                        hierarchy, topBeanMeta);
            } else {
                // No @GroupSequence on the bean class — each hierarchy class uses its own sequence
                validateBeanPerClassSequence(rootBean, rootBeanClass, bean, beanClass,
                        currentPath, groups, violations, visited, iterIndex, iterKey, inIterable,
                        containerClass, containerTypeArgIndex, executableParameters, executableReturnValue,
                        hierarchy);
            }
        } finally {
            // Remove from visited so the same object can be validated on different paths.
            // Per BV spec 4.6.1: cycle detection is per-path, not global.
            visited.remove(bean);
        }
    }

    /**
     * Validates a bean whose class declares @GroupSequence. The top-level sequence applies
     * for ALL constraints in the hierarchy, ensuring sequence isolation per BV spec 5.4.
     */
    private <T> void validateBeanWithTopLevelSequence(Object rootBean, Class<?> rootBeanClass,
            Object bean, Class<?> beanClass, PathImpl currentPath, Set<Class<?>> groups,
            Set<ConstraintViolation<T>> violations, Set<Object> visited,
            Integer iterIndex, Object iterKey, boolean inIterable,
            Class<?> containerClass, Integer containerTypeArgIndex,
            Object[] executableParameters, Object executableReturnValue,
            List<Class<?>> hierarchy, ConstrainedBeanMetadata topBeanMeta) {

        List<GroupSequenceStep> resolvedSteps = resolveGroupSequenceWithMode(topBeanMeta, groups);

        // Phase 1: Validate OWN constraints according to group sequence (with short-circuiting)
        boolean sequenceFailed = false;
        for (GroupSequenceStep step : resolvedSteps) {
            Set<Class<?>> stepGroups = step.groups();
            boolean isSequenced = step.sequenced();

            if (isSequenced && sequenceFailed) {
                continue;
            }

            int ownViolations = 0;

            for (Class<?> current : hierarchy) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
                if (beanMeta == null) {
                    continue;
                }

                Set<Class<?>> implicitGroupSet = null;
                if (current.isInterface()) {
                    implicitGroupSet = Collections.singleton(current);
                }
                Set<Class<?>> effectiveGroups = augmentWithImplicitGroup(stepGroups, implicitGroupSet);

                int beforeThisClass = violations.size();

                // Cache reachability results to avoid calling isReachable() more than once
                Map<String, Boolean> fieldReachable = new HashMap<>();
                Map<String, Boolean> getterReachable = new HashMap<>();

                // Validate constraints only (no cascading in sequence steps)
                validateBeanConstraintsForClass(rootBean, rootBeanClass, bean, beanClass,
                        currentPath, effectiveGroups, violations, visited,
                        iterIndex, iterKey, inIterable, containerClass, containerTypeArgIndex,
                        executableParameters, executableReturnValue, beanMeta,
                        fieldReachable, getterReachable);

                ownViolations += (violations.size() - beforeThisClass);
            }

            // Short-circuit based on the bean's OWN violations (not cascaded)
            if (isSequenced && ownViolations > 0) {
                sequenceFailed = true;
            }
        }

        // Phase 2: Cascading - done ONCE with original groups (not per sequence step)
        // Per BV spec 5.4: cascaded @Valid uses the original Default group,
        // and cascaded beans resolve their own @GroupSequence independently.
        // Cascading happens regardless of whether the sequence failed - cascaded beans
        // are independent and not part of the sequence short-circuit logic.
        {
            for (Class<?> current : hierarchy) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
                if (beanMeta == null) {
                    continue;
                }
                Map<String, Boolean> fieldReachable = new HashMap<>();
                Map<String, Boolean> getterReachable = new HashMap<>();

                // Re-check reachability for cascading (since we didn't cache from Phase 1)
                validateBeanCascadingForClass(rootBean, rootBeanClass, bean, beanClass,
                        currentPath, groups, violations, visited,
                        iterIndex, iterKey, inIterable, containerClass, containerTypeArgIndex,
                        executableParameters, executableReturnValue, beanMeta,
                        fieldReachable, getterReachable);
            }
        }
    }

    /**
     * Validates a bean whose class does NOT declare @GroupSequence. Each hierarchy class
     * may use its own sequence independently (original behavior).
     */
    private <T> void validateBeanPerClassSequence(Object rootBean, Class<?> rootBeanClass,
            Object bean, Class<?> beanClass, PathImpl currentPath, Set<Class<?>> groups,
            Set<ConstraintViolation<T>> violations, Set<Object> visited,
            Integer iterIndex, Object iterKey, boolean inIterable,
            Class<?> containerClass, Integer containerTypeArgIndex,
            Object[] executableParameters, Object executableReturnValue,
            List<Class<?>> hierarchy) {

        for (Class<?> current : hierarchy) {
            ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
            if (beanMeta != null) {
                List<GroupSequenceStep> steps = resolveGroupSequenceWithMode(beanMeta, groups);

                Set<Class<?>> implicitGroupSet = null;
                if (current.isInterface()) {
                    implicitGroupSet = Collections.singleton(current);
                }

                boolean sequenceFailed = false;
                for (GroupSequenceStep step : steps) {
                    Set<Class<?>> stepGroups = step.groups();
                    boolean isSequenced = step.sequenced();

                    if (isSequenced && sequenceFailed) {
                        continue;
                    }

                    int violationsBefore = violations.size();
                    Set<Class<?>> effectiveGroups = augmentWithImplicitGroup(stepGroups, implicitGroupSet);
                    boolean hasOwnSequence = beanMeta.groupSequence() != null && isSequenced;
                    Set<Class<?>> cascadeGroups = hasOwnSequence ? groups : effectiveGroups;

                    // Cache reachability results to avoid calling isReachable() more than once
                    Map<String, Boolean> fieldReachable = new HashMap<>();
                    Map<String, Boolean> getterReachable = new HashMap<>();

                    // Phase 1: Validate constraints
                    validateBeanConstraintsForClass(rootBean, rootBeanClass, bean, beanClass,
                            currentPath, effectiveGroups, violations, visited,
                            iterIndex, iterKey, inIterable, containerClass, containerTypeArgIndex,
                            executableParameters, executableReturnValue, beanMeta,
                            fieldReachable, getterReachable);

                    int parentOwnViolations = violations.size() - violationsBefore;

                    // Phase 2: Cascading
                    validateBeanCascadingForClass(rootBean, rootBeanClass, bean, beanClass,
                            currentPath, cascadeGroups, violations, visited,
                            iterIndex, iterKey, inIterable, containerClass, containerTypeArgIndex,
                            executableParameters, executableReturnValue, beanMeta,
                            fieldReachable, getterReachable);

                    if (isSequenced) {
                        if (hasOwnSequence && parentOwnViolations > 0) {
                            sequenceFailed = true;
                        } else if (!hasOwnSequence && violations.size() > violationsBefore) {
                            sequenceFailed = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Phase 1: Validates constraints (class-level, field, getter) for a single hierarchy class.
     * Populates reachability caches so Phase 2 (cascading) can avoid duplicate isReachable() calls.
     */
    private <T> void validateBeanConstraintsForClass(Object rootBean, Class<?> rootBeanClass,
            Object bean, Class<?> beanClass, PathImpl currentPath,
            Set<Class<?>> effectiveGroups, Set<ConstraintViolation<T>> violations,
            Set<Object> visited, Integer iterIndex, Object iterKey, boolean inIterable,
            Class<?> containerClass, Integer containerTypeArgIndex,
            Object[] executableParameters, Object executableReturnValue,
            ConstrainedBeanMetadata beanMeta,
            Map<String, Boolean> fieldReachable, Map<String, Boolean> getterReachable) {

        // Class-level constraints
        for (ConstraintMetadata constraint : beanMeta.classConstraints()) {
            if (matchesGroup(constraint, effectiveGroups)) {
                PathImpl.BeanNodeImpl beanNode = new PathImpl.BeanNodeImpl(null,
                        containerClass, containerTypeArgIndex);
                applyIterableContext(beanNode, inIterable, iterIndex, iterKey);
                PathImpl beanPath = currentPath.append(beanNode);
                validateConstraint(rootBean, rootBeanClass, bean, bean, constraint,
                        beanPath, null, executableParameters, executableReturnValue,
                        violations, null);
            }
        }

        // Field constraints
        for (ConstrainedFieldMetadata fieldMeta : beanMeta.fields()) {
            PathImpl.PropertyNodeImpl fieldNode = new PathImpl.PropertyNodeImpl(
                    fieldMeta.name(), containerClass, containerTypeArgIndex);
            applyIterableContext(fieldNode, inIterable, iterIndex, iterKey);
            PathImpl fieldPath = currentPath.append(fieldNode);

            boolean reachable = isReachable(bean, fieldNode, rootBeanClass, currentPath, ElementType.FIELD);
            if (fieldReachable != null) {
                fieldReachable.put(fieldMeta.name(), reachable);
            }
            if (!reachable) {
                continue;
            }

            Object fieldValue = getFieldValue(bean, beanClass, fieldMeta.name());
            validateFieldConstraints(rootBean, rootBeanClass, bean, fieldValue, fieldMeta,
                    fieldPath, effectiveGroups, violations,
                    executableParameters, executableReturnValue, visited);
        }

        // Getter-property constraints
        for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
            if (methodMeta.getter()) {
                String propertyName = getPropertyNameFromGetter(methodMeta.name());
                PathImpl.PropertyNodeImpl propNode = new PathImpl.PropertyNodeImpl(
                        propertyName, containerClass, containerTypeArgIndex);
                applyIterableContext(propNode, inIterable, iterIndex, iterKey);
                PathImpl propPath = currentPath.append(propNode);

                boolean reachable = isReachable(bean, propNode, rootBeanClass, currentPath, ElementType.METHOD);
                if (getterReachable != null) {
                    getterReachable.put(propertyName, reachable);
                }
                if (!reachable) {
                    continue;
                }

                Object propertyValue = getPropertyValue(bean, beanClass, methodMeta.name());
                validateReturnValueConstraints(rootBean, rootBeanClass, bean, propertyValue,
                        methodMeta, propPath, effectiveGroups, violations,
                        executableParameters, executableReturnValue);
            }
        }
    }

    /**
     * Phase 2: Cascading validation (@Valid) for a single hierarchy class.
     * Uses reachability caches from Phase 1 to avoid duplicate isReachable() calls.
     */
    private <T> void validateBeanCascadingForClass(Object rootBean, Class<?> rootBeanClass,
            Object bean, Class<?> beanClass, PathImpl currentPath,
            Set<Class<?>> cascadeGroups, Set<ConstraintViolation<T>> violations,
            Set<Object> visited, Integer iterIndex, Object iterKey, boolean inIterable,
            Class<?> containerClass, Integer containerTypeArgIndex,
            Object[] executableParameters, Object executableReturnValue,
            ConstrainedBeanMetadata beanMeta,
            Map<String, Boolean> fieldReachable, Map<String, Boolean> getterReachable) {

        // Track which properties have been cascaded via field access to avoid
        // duplicating cascading when a getter for the same property also has @Valid
        Set<String> cascadedProperties = new HashSet<>();

        // Field cascading
        for (ConstrainedFieldMetadata fieldMeta : beanMeta.fields()) {
            if (fieldMeta.cascading()) {
                PathImpl.PropertyNodeImpl fieldNode = new PathImpl.PropertyNodeImpl(
                        fieldMeta.name(), containerClass, containerTypeArgIndex);
                applyIterableContext(fieldNode, inIterable, iterIndex, iterKey);
                PathImpl fieldPath = currentPath.append(fieldNode);

                // Use cached reachability from Phase 1 if available
                Boolean cachedReachable = fieldReachable != null
                        ? fieldReachable.get(fieldMeta.name())
                        : null;
                boolean reachable = cachedReachable != null
                        ? cachedReachable
                        : isReachable(bean, fieldNode, rootBeanClass, currentPath, ElementType.FIELD);
                if (!reachable) {
                    continue;
                }
                if (!isCascadable(bean, fieldNode, rootBeanClass, currentPath, ElementType.FIELD)) {
                    continue;
                }

                Object fieldValue = getFieldValue(bean, beanClass, fieldMeta.name());
                if (fieldValue != null) {
                    cascadedProperties.add(fieldMeta.name());
                    // Resolve declared field type for correct containerClass on path nodes
                    Class<?> declaredFieldType = loadClass(fieldMeta.fieldTypeName());
                    cascadeValidation(rootBean, rootBeanClass, fieldValue, fieldPath,
                            cascadeGroups, violations, visited, fieldMeta.groupConversions(),
                            executableParameters, executableReturnValue, declaredFieldType);
                }
            }
        }

        // Getter cascading
        for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
            if (methodMeta.getter() && methodMeta.returnValueCascading()) {
                String propertyName = getPropertyNameFromGetter(methodMeta.name());
                // Skip if already cascaded via field access for the same property
                if (cascadedProperties.contains(propertyName)) {
                    continue;
                }
                PathImpl.PropertyNodeImpl propNode = new PathImpl.PropertyNodeImpl(
                        propertyName, containerClass, containerTypeArgIndex);
                applyIterableContext(propNode, inIterable, iterIndex, iterKey);
                PathImpl propPath = currentPath.append(propNode);

                // Use cached reachability from Phase 1 if available
                Boolean cachedReachable = getterReachable != null
                        ? getterReachable.get(propertyName)
                        : null;
                boolean reachable = cachedReachable != null
                        ? cachedReachable
                        : isReachable(bean, propNode, rootBeanClass, currentPath, ElementType.METHOD);
                if (!reachable) {
                    continue;
                }
                if (!isCascadable(bean, propNode, rootBeanClass, currentPath, ElementType.METHOD)) {
                    continue;
                }

                Object propertyValue = getPropertyValue(bean, beanClass, methodMeta.name());
                if (propertyValue != null) {
                    cascadeValidation(rootBean, rootBeanClass, propertyValue, propPath,
                            cascadeGroups, violations, visited,
                            methodMeta.returnValueGroupConversions(),
                            executableParameters, executableReturnValue, null);
                }
            }
        }
    }

    /**
     * Augments the requested groups with the implicit group from the declaring interface.
     * This implements implicit grouping: constraints on an interface with no explicit groups
     * should match when the interface is used as a validation group.
     */
    private Set<Class<?>> augmentWithImplicitGroup(Set<Class<?>> groups, Set<Class<?>> implicitGroups) {
        if (implicitGroups == null || implicitGroups.isEmpty()) {
            return groups;
        }
        // Only augment if any requested group (or its inherited groups) matches an implicit group
        for (Class<?> implicitGroup : implicitGroups) {
            for (Class<?> requestedGroup : groups) {
                if (requestedGroup == implicitGroup) {
                    // The interface itself is requested as a group — add Default so that
                    // Default-group constraints on this interface are included
                    Set<Class<?>> augmented = new LinkedHashSet<>(groups);
                    augmented.add(Default.class);
                    return augmented;
                }
                // Check if the requested group inherits from the implicit group
                Set<Class<?>> inherited = expandGroupWithInheritance(requestedGroup);
                if (inherited.contains(implicitGroup)) {
                    Set<Class<?>> augmented = new LinkedHashSet<>(groups);
                    augmented.add(Default.class);
                    return augmented;
                }
            }
        }
        return groups;
    }

    private void applyIterableContext(PathImpl.NodeImpl node, boolean inIterable,
            Integer iterIndex, Object iterKey) {
        if (inIterable) {
            if (iterIndex != null) {
                node.setIndex(iterIndex);
            } else if (iterKey != null) {
                node.setKey(iterKey);
            } else {
                node.setInIterable(true);
            }
        }
    }

    private List<Class<?>> getTypeHierarchy(Class<?> clazz) {
        return typeHierarchyCache.computeIfAbsent(clazz, new Function<>() {
            @Override
            public List<Class<?>> apply(Class<?> c) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(c.getName());
                if (beanMeta == null || beanMeta.typeHierarchy() == null) {
                    // Class not in build-time metadata — either unconstrained or runtime-generated
                    // (e.g., CDI proxies, config mapping implementations).
                    // Walk the class hierarchy to find the parent with metadata.
                    List<Class<?>> hierarchy = new ArrayList<>();
                    Set<Class<?>> seen = new HashSet<>();
                    collectRuntimeTypeHierarchy(c, hierarchy, seen);
                    return List.copyOf(hierarchy);
                }
                List<Class<?>> hierarchy = new ArrayList<>();
                for (String typeName : beanMeta.typeHierarchy()) {
                    Class<?> loaded = loadClass(typeName);
                    if (loaded != null) {
                        hierarchy.add(loaded);
                    }
                }
                return List.copyOf(hierarchy);
            }
        });
    }

    private static void collectRuntimeTypeHierarchy(Class<?> clazz, List<Class<?>> result, Set<Class<?>> seen) {
        if (clazz == null || clazz == Object.class || !seen.add(clazz)) {
            return;
        }
        result.add(clazz);
        collectRuntimeTypeHierarchy(clazz.getSuperclass(), result, seen);
        for (Class<?> iface : clazz.getInterfaces()) {
            collectRuntimeTypeHierarchy(iface, result, seen);
        }
    }

    private <T> void validateFieldConstraints(Object rootBean, Class<?> rootBeanClass,
            Object leafBean, Object value, ConstrainedFieldMetadata fieldMeta, PathImpl path,
            Set<Class<?>> groups, Set<ConstraintViolation<T>> violations,
            Object[] executableParameters, Object executableReturnValue,
            Set<Object> visited) {
        // Use build-time metadata for the declared field type instead of runtime reflection
        Class<?> declaredType = loadClass(fieldMeta.fieldTypeName());
        for (ConstraintMetadata constraint : fieldMeta.constraints()) {
            if (matchesGroup(constraint, groups)) {
                validateConstraint(rootBean, rootBeanClass, leafBean, value, constraint, path,
                        declaredType, executableParameters, executableReturnValue, violations, null);
            }
        }

        // Container element constraints
        if (value != null) {
            // When the field itself has @Valid, suppress container element cascading to avoid
            // duplicate cascading (field-level @Valid cascading in Phase 2 handles it)
            boolean suppressCascading = fieldMeta.cascading();
            for (ContainerElementConstraint cec : fieldMeta.containerElementConstraints()) {
                validateContainerElementConstraints(rootBean, rootBeanClass, leafBean, value, cec, path, groups,
                        violations, executableParameters, executableReturnValue, visited, suppressCascading);
            }
        }
    }

    private <T> void validateReturnValueConstraints(Object rootBean, Class<?> rootBeanClass,
            Object leafBean, Object value, ConstrainedMethodMetadata methodMeta, PathImpl path,
            Set<Class<?>> groups, Set<ConstraintViolation<T>> violations,
            Object[] executableParameters, Object executableReturnValue) {
        // Resolve the declared return type for correct validator lookup when value is null
        Class<?> declaredType = null;
        if (value == null) {
            Class<?> declaringClass = loadClass(methodMeta.declaringClassName());
            try {
                Method m = declaringClass.getMethod(methodMeta.name());
                declaredType = m.getReturnType();
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "Method not found: " + methodMeta.name() + " on " + methodMeta.declaringClassName()
                                + ". Ensure the class is included in the Jandex index.",
                        e);
            }
        }
        for (ConstraintMetadata constraint : methodMeta.returnValueConstraints()) {
            if (matchesGroup(constraint, groups)) {
                validateConstraint(rootBean, rootBeanClass, leafBean, value, constraint, path, declaredType,
                        executableParameters, executableReturnValue, violations, null);
            }
        }

        if (value != null) {
            // When the return value has @Valid, suppress container element cascading
            // to avoid duplicates (return value cascading in the caller handles it)
            boolean suppressCascading = methodMeta.returnValueCascading();
            for (ContainerElementConstraint cec : methodMeta.returnValueContainerElementConstraints()) {
                validateContainerElementConstraints(rootBean, rootBeanClass, leafBean, value, cec, path, groups,
                        violations, executableParameters, executableReturnValue, null, suppressCascading);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> void validateContainerElementConstraints(Object rootBean, Class<?> rootBeanClass,
            Object leafBean, Object containerValue, ContainerElementConstraint cec, PathImpl basePath,
            Set<Class<?>> groups, Set<ConstraintViolation<T>> violations,
            Object[] executableParameters, Object executableReturnValue,
            Set<Object> parentVisited, boolean suppressCascading) {
        int typeArgIdx = cec.typeArgumentIndex();

        // Resolve the declared container class from metadata (e.g., java.util.List)
        // rather than using the runtime class (e.g., java.util.Arrays$ArrayList)
        Class<?> containerClass = resolveContainerClass(cec, containerValue);

        // Use parent's visited set for cycle detection across cascading levels.
        // When suppressCascading is true (field-level @Valid handles cascading in Phase 2),
        // set visited to null to prevent duplicate cascading from container element @Valid.
        Set<Object> visited = (cec.cascading() && !suppressCascading)
                ? (parentVisited != null ? parentVisited : Collections.newSetFromMap(new IdentityHashMap<>()))
                : null;

        if (containerValue instanceof List list) {
            int idx = 0;
            for (Object element : list) {
                PathImpl elementPath = basePath.append(
                        new PathImpl.ContainerElementNodeImpl("<list element>",
                                containerClass, typeArgIdx));
                PathImpl.NodeImpl lastNode = getLastNode(elementPath);
                if (lastNode != null) {
                    lastNode.setIndex(idx);
                }
                validateAndCascadeElement(rootBean, rootBeanClass, leafBean, element, cec, elementPath,
                        basePath,
                        groups, violations, executableParameters, executableReturnValue,
                        visited, containerClass, typeArgIdx, idx, null);
                idx++;
            }
        } else if (containerValue instanceof Collection collection) {
            for (Object element : collection) {
                PathImpl elementPath = basePath.append(
                        new PathImpl.ContainerElementNodeImpl("<iterable element>",
                                containerClass, typeArgIdx));
                PathImpl.NodeImpl lastNode = getLastNode(elementPath);
                if (lastNode != null) {
                    lastNode.setInIterable(true);
                }
                validateAndCascadeElement(rootBean, rootBeanClass, leafBean, element, cec, elementPath,
                        basePath,
                        groups, violations, executableParameters, executableReturnValue,
                        visited, containerClass, typeArgIdx, null, null);
            }
        } else if (containerValue instanceof Iterable iterable) {
            for (Object element : iterable) {
                PathImpl elementPath = basePath.append(
                        new PathImpl.ContainerElementNodeImpl("<iterable element>",
                                containerClass, typeArgIdx));
                PathImpl.NodeImpl lastNode = getLastNode(elementPath);
                if (lastNode != null) {
                    lastNode.setInIterable(true);
                }
                validateAndCascadeElement(rootBean, rootBeanClass, leafBean, element, cec, elementPath,
                        basePath,
                        groups, violations, executableParameters, executableReturnValue,
                        visited, containerClass, typeArgIdx, null, null);
            }
        } else if (containerValue instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object target = typeArgIdx == 0 ? entry.getKey() : entry.getValue();
                PathImpl elementPath = basePath.append(
                        new PathImpl.ContainerElementNodeImpl("<map " + (typeArgIdx == 0 ? "key" : "value") + ">",
                                containerClass, typeArgIdx));
                PathImpl.NodeImpl lastNode = getLastNode(elementPath);
                if (lastNode != null) {
                    lastNode.setKey(entry.getKey());
                }
                validateAndCascadeElement(rootBean, rootBeanClass, leafBean, target, cec, elementPath,
                        basePath,
                        groups, violations, executableParameters, executableReturnValue,
                        visited, containerClass, typeArgIdx, null, entry.getKey());
            }
        } else if (containerValue != null && containerValue.getClass().isArray()) {
            int length = Array.getLength(containerValue);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(containerValue, i);
                PathImpl elementPath = basePath.append(
                        new PathImpl.ContainerElementNodeImpl("<array element>",
                                containerClass, typeArgIdx));
                PathImpl.NodeImpl lastNode = getLastNode(elementPath);
                if (lastNode != null) {
                    lastNode.setIndex(i);
                }
                validateAndCascadeElement(rootBean, rootBeanClass, leafBean, element, cec, elementPath,
                        basePath,
                        groups, violations, executableParameters, executableReturnValue,
                        visited, containerClass, typeArgIdx, i, null);
            }
        } else if (containerValue instanceof Optional<?> opt) {
            Object element = opt.orElse(null);
            // Optional: validate constraints at basePath (no container element node)
            // Per BV spec: leafBean is the bean hosting the container, not the element
            for (ConstraintMetadata constraint : cec.constraints()) {
                if (matchesGroup(constraint, groups)) {
                    validateConstraint(rootBean, rootBeanClass, leafBean, element,
                            constraint, basePath, null, executableParameters,
                            executableReturnValue, violations, null);
                }
            }
            // Cascade into Optional value — cascaded properties carry Optional container info
            if (cec.cascading() && !suppressCascading && element != null) {
                validateBean(rootBean, rootBeanClass, element, element.getClass(),
                        basePath, groups, (Set) violations, visited,
                        null, null, false,
                        java.util.Optional.class, 0,
                        executableParameters, executableReturnValue);
            }
            // Process nested container element constraints on the unwrapped Optional value
            List<ContainerElementConstraint> nestedOpt = cec.nestedContainerElements();
            if (nestedOpt != null && !nestedOpt.isEmpty() && element != null) {
                for (ContainerElementConstraint nestedCec : nestedOpt) {
                    validateContainerElementConstraints(rootBean, rootBeanClass, leafBean,
                            element, nestedCec, basePath, groups, violations,
                            executableParameters, executableReturnValue, visited, false);
                }
            }
        } else {
            // Handle OptionalInt/OptionalLong/OptionalDouble via unwrapOptionalValue
            UnwrappedValue optUnwrapped = unwrapOptionalValue(containerValue);
            if (optUnwrapped != null) {
                for (ConstraintMetadata constraint : cec.constraints()) {
                    if (matchesGroup(constraint, groups)) {
                        validateConstraint(rootBean, rootBeanClass, leafBean, optUnwrapped.value,
                                constraint, basePath, null, executableParameters,
                                executableReturnValue, violations, null);
                    }
                }
            } else if (containerValue != null && !customValueExtractors.isEmpty()) {
                // Try custom value extractors
                for (ValueExtractor<?> extractor : customValueExtractors) {
                    if (isExtractorApplicable(extractor, containerValue.getClass())) {
                        @SuppressWarnings("unchecked")
                        ValueExtractor<Object> typedExtractor = (ValueExtractor<Object>) extractor;
                        typedExtractor.extractValues(containerValue,
                                new CustomValueExtractorReceiver<>(rootBean, rootBeanClass, leafBean,
                                        cec, basePath, groups, violations, executableParameters,
                                        executableReturnValue));
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> void validateAndCascadeElement(Object rootBean, Class<?> rootBeanClass,
            Object leafBean, Object element, ContainerElementConstraint cec, PathImpl elementPath,
            PathImpl basePath,
            Set<Class<?>> groups, Set<ConstraintViolation<T>> violations,
            Object[] executableParameters, Object executableReturnValue,
            Set<Object> visited, Class<?> containerClass, int typeArgIdx,
            Integer iterIndex, Object iterKey) {
        // Validate constraints on the element (uses elementPath with container element node)
        // Per BV spec: leafBean is the bean hosting the container, not the element itself
        for (ConstraintMetadata constraint : cec.constraints()) {
            if (matchesGroup(constraint, groups)) {
                validateConstraint(rootBean, rootBeanClass, leafBean, element,
                        constraint, elementPath, null, executableParameters,
                        executableReturnValue, violations, null);
            }
        }
        // Cascade into the element if @Valid is present on the type argument.
        // Per BV spec: cascaded property paths use basePath (without container element node)
        // with container class/typeArgIdx on the property nodes themselves.
        if (cec.cascading() && element != null && visited != null) {
            // Apply container element group conversions (e.g., @ConvertGroup on type argument)
            Set<Class<?>> cascadeGroups = groups;
            Map<String, String> groupConversions = cec.groupConversions();
            if (groupConversions != null && !groupConversions.isEmpty()) {
                cascadeGroups = convertGroups(groups, groupConversions);
            }
            // Check if converted groups contain a @GroupSequence interface
            List<Set<Class<?>>> sequenceSteps = resolveGroupSequenceInterfaces(cascadeGroups);
            if (sequenceSteps != null) {
                for (Set<Class<?>> stepGroups : sequenceSteps) {
                    int before = violations.size();
                    Set<Object> stepVisited = Collections.newSetFromMap(new IdentityHashMap<>());
                    stepVisited.addAll(visited);
                    validateBean(rootBean, rootBeanClass, element, element.getClass(),
                            basePath, stepGroups, (Set) violations, stepVisited,
                            iterIndex, iterKey, true, containerClass, typeArgIdx,
                            executableParameters, executableReturnValue);
                    if (violations.size() > before) {
                        break;
                    }
                }
            } else {
                validateBean(rootBean, rootBeanClass, element, element.getClass(),
                        basePath, cascadeGroups, (Set) violations, visited,
                        iterIndex, iterKey, true, containerClass, typeArgIdx,
                        executableParameters, executableReturnValue);
            }
        }
        // Process nested container element constraints (e.g., Map<K, List<@Valid V>>).
        // After extracting the element from the outer container, apply nested constraints
        // on the element which is itself a container.
        // Use elementPath (with container element node) so nested paths build correctly:
        // e.g., map.<map value>.<list element> for Map<K, List<@Size V>>
        List<ContainerElementConstraint> nested = cec.nestedContainerElements();
        if (nested != null && !nested.isEmpty() && element != null) {
            for (ContainerElementConstraint nestedCec : nested) {
                validateContainerElementConstraints(rootBean, rootBeanClass, leafBean,
                        element, nestedCec, elementPath, groups, violations,
                        executableParameters, executableReturnValue, visited, false);
            }
        }
    }

    private Class<?> resolveContainerClass(ContainerElementConstraint cec, Object containerValue) {
        String containerClassName = cec.containerClassName();
        if (containerClassName != null) {
            try {
                return Class.forName(containerClassName, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Container class not found: " + containerClassName
                                + ". Ensure the class is included in the Jandex index.",
                        e);
            }
        }
        return containerValue != null ? containerValue.getClass() : Object.class;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> void validateConstraint(Object rootBean, Class<?> rootBeanClass,
            Object leafBean, Object value, ConstraintMetadata constraint,
            PathImpl path, Class<?> declaredType,
            Object[] executableParameters, Object executableReturnValue,
            Set<ConstraintViolation<T>> violations,
            List<String> parameterNames) {

        // Validate constraint definition
        validateConstraintDefinition(constraint.annotationClassName());

        // Handle composing constraints
        if (constraint.composingConstraints() != null && !constraint.composingConstraints().isEmpty()) {
            Set<ConstraintViolation<T>> composingViolations = new LinkedHashSet<>();
            // Per BV spec 3.3: composing constraints inherit groups and payload from the composed constraint
            List<String> inheritedGroups = constraint.groups();
            List<String> inheritedPayload = constraint.payload();
            // Resolve @OverridesAttribute mappings from the composed annotation
            Map<String, Map<String, Object>> overridesMap = resolveOverridesAttributes(constraint);
            // Track per-type index for @OverridesAttribute constraintIndex
            Map<String, Integer> typeIndexCounters = new HashMap<>();
            for (ConstraintMetadata composing : constraint.composingConstraints()) {
                String composingType = composing.annotationClassName();
                int typeIndex = typeIndexCounters.getOrDefault(composingType, 0);
                typeIndexCounters.put(composingType, typeIndex + 1);
                // Build attribute overrides for this composing constraint
                Map<String, Object> attrOverrides = getAttributeOverridesForComposing(
                        overridesMap, composingType, typeIndex);
                // Validate that override attribute types match the composing constraint's attribute types
                validateOverrideAttributeTypes(composingType, attrOverrides, composing.attributeTypes());
                // Per BV spec 3.3: propagate validationAppliesTo from composed to composing constraints
                Object parentVat = constraint.attributes() != null
                        ? constraint.attributes().get("validationAppliesTo")
                        : null;
                if (parentVat != null && !ConstraintTarget.IMPLICIT.name().equals(String.valueOf(parentVat))) {
                    attrOverrides = !attrOverrides.isEmpty()
                            ? new LinkedHashMap<>(attrOverrides)
                            : new LinkedHashMap<>();
                    attrOverrides.put("validationAppliesTo", parentVat);
                }
                ConstraintMetadata inherited = composing.withInheritedGroupsPayloadAndAttributes(
                        inheritedGroups, inheritedPayload, attrOverrides);
                validateConstraint(rootBean, rootBeanClass, leafBean, value, inherited, path, declaredType,
                        executableParameters, executableReturnValue, composingViolations, parameterNames);
            }
            if (constraint.reportAsSingleViolation()) {
                if (!composingViolations.isEmpty()) {
                    // Report as single violation using the composed constraint's message
                    ConstraintDescriptor<?> descriptor = constraintDescriptorCache.computeIfAbsent(constraint,
                            new Function<>() {
                                @Override
                                public ConstraintDescriptor<?> apply(ConstraintMetadata cm) {
                                    return createConstraintDescriptor(cm);
                                }
                            });
                    String messageTemplate = getMessageTemplate(constraint);
                    String message = interpolateMessage(messageTemplate, value, descriptor);
                    violations.add(new QuarkusConstraintViolation(
                            messageTemplate, message,
                            (T) rootBean, (Class<T>) rootBeanClass,
                            leafBean, value, path, descriptor,
                            executableParameters, executableReturnValue));
                }
                return;
            } else {
                violations.addAll(composingViolations);
                // For non-@ReportAsSingleViolation, the main validator still runs
            }
        }

        // Unwrap OptionalInt/OptionalLong/OptionalDouble/Optional before validator lookup
        // Per BV spec: built-in value extractors for these types are @UnwrapByDefault
        Object validatedValue = value;
        Class<?> effectiveDeclaredType = declaredType;
        UnwrappedValue unwrapped = unwrapOptionalValue(value);
        if (unwrapped != null) {
            validatedValue = unwrapped.value;
            effectiveDeclaredType = unwrapped.declaredType;
        }

        // Handle Unwrapping.Unwrap payload: extract values from container before validation.
        // Per BV spec 5.7.4: when Unwrapping.Unwrap is in the payload, use value extraction
        // to unwrap the container and validate each extracted value.
        if (validatedValue != null && hasUnwrapPayload(constraint)) {
            if (validatedValue instanceof Iterable<?> iterable) {
                Class<?> elementType = resolveIterableTypeArgument(validatedValue.getClass());
                for (Object element : iterable) {
                    Class<?> resolvedType = element != null ? element.getClass()
                            : (elementType != null ? elementType : Object.class);
                    validateConstraint(rootBean, rootBeanClass, leafBean, element, constraint, path,
                            resolvedType, executableParameters, executableReturnValue, violations,
                            parameterNames);
                }
                return;
            }
        }

        // Get the validator class
        String validatorClassName = constraint.validatorClassName();
        Class<? extends ConstraintValidator> validatorClass;
        Class<?> validatedValueClass = validatedValue != null ? validatedValue.getClass()
                : (effectiveDeclaredType != null ? effectiveDeclaredType : Object.class);
        String errorMessage = "No validator could be found for constraint '"
                + constraint.annotationClassName()
                + "' validating type '" + validatedValueClass.getName()
                + "'. Check configuration for '" + path + "'";
        if (validatorClassName == null) {
            // Try to find from built-in registry, using declared type when value is null
            // First try with the runt
            // ime value type, then with the declared type
            validatorClass = BuiltinConstraintValidators
                    .findValidator(constraint.annotationClassName(), validatedValueClass);
            if (validatorClass == null && effectiveDeclaredType != null && effectiveDeclaredType != validatedValueClass) {
                validatorClass = BuiltinConstraintValidators
                        .findValidator(constraint.annotationClassName(), effectiveDeclaredType);
            }
            if (validatorClass == null) {
                // Try to find a custom validator from the constraint definition
                validatorClass = findCustomValidator(constraint.annotationClassName(), validatedValueClass);
                if (validatorClass == null && effectiveDeclaredType != null && effectiveDeclaredType != validatedValueClass) {
                    validatorClass = findCustomValidator(constraint.annotationClassName(), effectiveDeclaredType);
                }
            }
            if (validatorClass == null && validatedValue == null
                    && (effectiveDeclaredType == null || effectiveDeclaredType == Object.class)) {
                // When value is null and we can't resolve the type, pick the first
                // available validator. All validators for a given constraint should
                // agree on null handling (e.g., @NotBlank returns false for null,
                // @Size returns true for null).
                List<Class<? extends ConstraintValidator<?, ?>>> allValidators = BuiltinConstraintValidators
                        .getValidators(constraint.annotationClassName());
                if (allValidators != null && !allValidators.isEmpty()) {
                    validatorClass = allValidators.get(0);
                }
            }
            if (validatorClass == null) {
                // Composed constraints with composing constraints but no own validator
                // are valid — validation is handled by the composing constraints
                if (constraint.composingConstraints() != null
                        && !constraint.composingConstraints().isEmpty()) {
                    return;
                }
                // Per BV spec: if no validator exists for the declared type, throw
                // UnexpectedTypeException even when the value is null
                if (effectiveDeclaredType != null && effectiveDeclaredType != Object.class) {
                    throw new UnexpectedTypeException(
                            "No validator could be found for constraint '"
                                    + constraint.annotationClassName()
                                    + "' validating type '" + effectiveDeclaredType.getName()
                                    + "'. Check configuration for '" + path + "'");
                }
                if (value == null) {
                    // null value with unknown declared type and no validator — skip
                    return;
                }
                throw new UnexpectedTypeException(errorMessage);
            }
        } else {
            // For custom constraints with a stored validator, try to find the best match
            // for the actual value type if there are multiple validators
            Class<? extends ConstraintValidator> bestMatch = findCustomValidator(
                    constraint.annotationClassName(), validatedValueClass);
            if (bestMatch != null) {
                validatorClass = bestMatch;
            } else {
                validatorClass = (Class<? extends ConstraintValidator>) loadClass(validatorClassName);
                if (validatorClass == null) {
                    throw new ValidationException("Cannot find validator class: " + validatorClassName);
                }
                // Check if stored validator can handle the value type
                Class<?> storedTargetType = getValidatorTargetType(validatorClass);
                if (storedTargetType != null && !storedTargetType.isAssignableFrom(validatedValueClass)) {
                    // Stored validator can't handle this value type.
                    if (constraint.composingConstraints() != null
                            && !constraint.composingConstraints().isEmpty()) {
                        return; // Composing constraints handle validation
                    }
                    throw new UnexpectedTypeException(errorMessage);
                }
            }
        }

        ConstraintDescriptor<?> descriptor = constraintDescriptorCache.computeIfAbsent(constraint,
                new Function<>() {
                    @Override
                    public ConstraintDescriptor<?> apply(ConstraintMetadata cm) {
                        return createConstraintDescriptor(cm);
                    }
                });

        @SuppressWarnings("unchecked")
        ConstraintValidator validator = getOrCreateValidator(constraint,
                (Class<? extends ConstraintValidator<?, ?>>) validatorClass);

        // Determine constraint kind from the path for ConstraintValidatorContext validation
        jakarta.validation.ElementKind constraintKind = null;
        for (Path.Node node : path) {
            constraintKind = node.getKind();
        }

        QuarkusConstraintValidatorContext context = new QuarkusConstraintValidatorContext(
                descriptor, path, clockProvider, constraintKind, parameterNames);

        boolean valid;
        try {
            valid = validator.isValid(validatedValue, context);
        } catch (RuntimeException e) {
            throw new ValidationException("Exception in ConstraintValidator.isValid(): " + e.getMessage(), e);
        }

        if (!valid) {
            // Per BV spec: if default is disabled and no custom violations were added, throw
            if (context.isDefaultDisabled() && context.getCustomViolations().isEmpty()) {
                throw new ValidationException(
                        "At least one custom ConstraintViolation must be created if the default "
                                + "ConstraintViolation is disabled.");
            }
            if (!context.isDefaultDisabled()) {
                String messageTemplate = getMessageTemplate(constraint);
                String message = interpolateMessage(messageTemplate, value, descriptor);
                violations.add(new QuarkusConstraintViolation(
                        messageTemplate, message,
                        rootBean, rootBeanClass,
                        leafBean, value, path, descriptor,
                        executableParameters, executableReturnValue));
            }
            // Add custom violations
            for (QuarkusConstraintValidatorContext.ViolationEntry custom : context.getCustomViolations()) {
                String message = interpolateMessage(custom.messageTemplate(), value, descriptor);
                violations.add(new QuarkusConstraintViolation(
                        custom.messageTemplate(), message,
                        rootBean, rootBeanClass,
                        leafBean, value, custom.path(), descriptor,
                        executableParameters, executableReturnValue));
            }
        }

        constraintValidatorFactory.releaseInstance(validator);
    }

    private <T> void cascadeValidation(Object rootBean, Class<?> rootBeanClass,
            Object value, PathImpl path, Set<Class<?>> groups,
            Set<ConstraintViolation<T>> violations, Set<Object> visited,
            Map<String, String> groupConversions,
            Object[] executableParameters, Object executableReturnValue,
            Class<?> declaredFieldType) {

        Set<Class<?>> convertedGroups = convertGroups(groups, groupConversions);

        // Check if any converted group is a @GroupSequence interface that needs expansion
        List<Set<Class<?>>> sequenceSteps = resolveGroupSequenceInterfaces(convertedGroups);
        if (sequenceSteps != null) {
            // Process as group sequence with short-circuiting
            for (Set<Class<?>> stepGroups : sequenceSteps) {
                int before = violations.size();
                // Use a fresh visited set per step to allow re-visiting for different groups
                Set<Object> stepVisited = Collections.newSetFromMap(new IdentityHashMap<>());
                stepVisited.addAll(visited);
                cascadeValidationForGroups(rootBean, rootBeanClass, value, path, stepGroups,
                        violations, stepVisited, executableParameters, executableReturnValue,
                        declaredFieldType);
                if (violations.size() > before) {
                    break; // Short-circuit: stop at first step with violations
                }
            }
        } else {
            cascadeValidationForGroups(rootBean, rootBeanClass, value, path, convertedGroups,
                    violations, visited, executableParameters, executableReturnValue,
                    declaredFieldType);
        }
    }

    private <T> void cascadeValidationForGroups(Object rootBean, Class<?> rootBeanClass,
            Object value, PathImpl path, Set<Class<?>> groups,
            Set<ConstraintViolation<T>> violations, Set<Object> visited,
            Object[] executableParameters, Object executableReturnValue,
            Class<?> declaredFieldType) {
        // For non-generic container subclasses (e.g., MyList extends ArrayList<String>),
        // the BV spec requires using the declared type as containerClass with null typeArgIndex,
        // rather than the generic superclass (e.g., List.class with typeArgIndex=0).
        boolean useNonGenericDeclaredType = declaredFieldType != null
                && declaredFieldType.getTypeParameters().length == 0;

        if (value instanceof Collection collection) {
            boolean isIndexed = collection instanceof List;
            Class<?> containerClass;
            Integer typeArgIdx;
            if (useNonGenericDeclaredType) {
                containerClass = declaredFieldType;
                typeArgIdx = null;
            } else {
                containerClass = value instanceof List ? List.class
                        : (value instanceof java.util.Set ? java.util.Set.class : Collection.class);
                typeArgIdx = 0;
            }
            int idx = 0;
            for (Object element : collection) {
                if (element != null) {
                    validateBean(rootBean, rootBeanClass, element, element.getClass(),
                            path, groups, violations, visited,
                            isIndexed ? idx : null, null, true,
                            containerClass, typeArgIdx,
                            executableParameters, executableReturnValue);
                }
                idx++;
            }
        } else if (value instanceof Map<?, ?> map) {
            Class<?> containerClass = useNonGenericDeclaredType ? declaredFieldType : Map.class;
            Integer typeArgIdx = useNonGenericDeclaredType ? null : 1;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object mapValue = entry.getValue();
                if (mapValue != null) {
                    validateBean(rootBean, rootBeanClass, mapValue, mapValue.getClass(),
                            path, groups, violations, visited, null, entry.getKey(), true,
                            containerClass, typeArgIdx,
                            executableParameters, executableReturnValue);
                }
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                if (element != null) {
                    validateBean(rootBean, rootBeanClass, element, element.getClass(),
                            path, groups, violations, visited, i, null, true,
                            Object[].class, null,
                            executableParameters, executableReturnValue);
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            Class<?> containerClass = useNonGenericDeclaredType ? declaredFieldType : Iterable.class;
            Integer typeArgIdx = useNonGenericDeclaredType ? null : 0;
            int idx = 0;
            for (Object element : iterable) {
                if (element != null) {
                    validateBean(rootBean, rootBeanClass, element, element.getClass(),
                            path, groups, violations, visited,
                            null, null, true,
                            containerClass, typeArgIdx,
                            executableParameters, executableReturnValue);
                }
                idx++;
            }
        } else {
            validateBean(rootBean, rootBeanClass, value, value.getClass(),
                    path, groups, violations, visited,
                    null, null, false, null, null,
                    executableParameters, executableReturnValue);
        }
    }

    // --- Method/Constructor validation support (used by QuarkusExecutableValidator) ---

    private List<Set<Class<?>>> buildGroupSequenceSteps(ConstrainedBeanMetadata beanMeta) {
        return groupSequenceStepsCache.computeIfAbsent(beanMeta.className(),
                new Function<>() {
                    @Override
                    public List<Set<Class<?>>> apply(String k) {
                        List<Set<Class<?>>> steps = new ArrayList<>();
                        for (String groupClassName : beanMeta.groupSequence()) {
                            Set<Class<?>> singleGroup = new HashSet<>();
                            Class<?> groupClass = loadClass(groupClassName);
                            singleGroup.add(groupClass);
                            if (groupClass.getName().equals(beanMeta.className())) {
                                singleGroup.add(Default.class);
                            }
                            steps.add(singleGroup);
                        }
                        return steps;
                    }
                });
    }

    private List<Set<Class<?>>> resolveBeanGroupSequence(Class<?> beanClass, Set<Class<?>> groupSet) {
        if (!groupSet.contains(Default.class)) {
            return null;
        }
        ConstrainedBeanMetadata beanMeta = metadata.getBean(beanClass.getName());
        if (beanMeta == null || beanMeta.groupSequence() == null) {
            return null;
        }
        return buildGroupSequenceSteps(beanMeta);
    }

    /**
     * Resolves group sequence steps for executable validation. Handles both:
     * 1. Explicit @GroupSequence interfaces passed as groups
     * 2. Declaring class's @GroupSequence redefining Default
     *
     * @return list of group sets for sequenced iteration, or null if no sequence applies
     */
    private List<Set<Class<?>>> resolveExecutableGroupSequence(Set<Class<?>> groupSet,
            Class<?> declaringClass) {
        // Check for explicit @GroupSequence interfaces
        List<Set<Class<?>>> seqSteps = resolveGroupSequenceInterfaces(groupSet);
        if (seqSteps != null) {
            return seqSteps;
        }

        // Check for declaring class's @GroupSequence redefining Default
        if (groupSet.contains(Default.class)) {
            ConstrainedBeanMetadata declaringBeanMeta = metadata.getBean(declaringClass.getName());
            if (declaringBeanMeta != null && declaringBeanMeta.groupSequence() != null) {
                return buildGroupSequenceSteps(declaringBeanMeta);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<ConstraintViolation<T>> validateParameters(T object, Method method,
            Object[] parameterValues, Class<?>... groups) {
        if (parameterValues == null) {
            throw new IllegalArgumentException("Parameter values must not be null");
        }

        // Validate constraint definitions on the method
        validateMethodConstraintDefinitions(method);

        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        Class<?> beanClass = object != null ? object.getClass() : method.getDeclaringClass();

        // Resolve group sequences (explicit @GroupSequence or bean-level @GroupSequence)
        List<Set<Class<?>>> seqSteps = resolveExecutableGroupSequence(groupSet, method.getDeclaringClass());
        List<Set<Class<?>>> groupSteps = seqSteps != null ? seqSteps : Collections.singletonList(groupSet);
        // Per spec: for cascaded validation during group sequence, Default is propagated
        Set<Class<?>> defaultGroup = Collections.singleton(Default.class);

        // Track which parameter indices have been cascaded to avoid duplicates
        // across hierarchy levels and group sequence steps
        Set<Integer> cascadedParams = new HashSet<>();
        for (Set<Class<?>> stepGroups : groupSteps) {
            int beforeDirect = violations.size();
            boolean hasDirectViolations = false;
            List<Class<?>> paramTypes = Arrays.asList(method.getParameterTypes());
            PathImpl methodPath = PathImpl.createRootPath()
                    .append(new PathImpl.MethodNodeImpl(method.getName(), paramTypes));

            // Walk the type hierarchy to find method constraints (including inherited ones)
            for (Class<?> current : getTypeHierarchy(beanClass)) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
                if (beanMeta == null) {
                    continue;
                }

                for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
                    if (matchesMethod(methodMeta, method)) {
                        // Validate group conversion rules
                        validateGroupConversionsForExecutable(methodMeta, method.getName());

                        // Cross-parameter constraints (direct)
                        List<String> crossParamNames = methodMeta.crossParameterConstraints().isEmpty()
                                ? null
                                : parameterNameProvider.getParameterNames(method);
                        for (ConstraintMetadata constraint : methodMeta.crossParameterConstraints()) {
                            if (matchesGroup(constraint, stepGroups)) {
                                PathImpl crossParamPath = methodPath
                                        .append(new PathImpl.CrossParameterNodeImpl());
                                //noinspection rawtypes
                                validateConstraint(object, beanClass, object, parameterValues,
                                        constraint, crossParamPath, null, parameterValues, null,
                                        (Set) violations, crossParamNames);
                            }
                        }

                        // Direct parameter constraints (no cascading yet)
                        List<String> methodParamNames = parameterNameProvider.getParameterNames(method);
                        validateMethodParametersDirect(object, beanClass, methodMeta, parameterValues,
                                methodPath, stepGroups, violations, methodParamNames);

                        // Track direct violation count for short-circuiting decision
                        hasDirectViolations = violations.size() > beforeDirect;

                        // Cascaded parameter validation — cascade each parameter only once
                        Set<Class<?>> cascadeGroups = seqSteps != null ? defaultGroup : stepGroups;
                        validateMethodParametersCascadingOnce(object, beanClass, methodMeta, parameterValues,
                                methodPath, cascadeGroups, violations, methodParamNames, cascadedParams);
                        break;
                    }
                }
            }
            // Short-circuit: only based on direct constraint violations, not cascaded ones
            if (seqSteps != null && hasDirectViolations) {
                break;
            }
        }

        return violations;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<ConstraintViolation<T>> validateReturnValue(T object, Method method,
            Object returnValue, Class<?>... groups) {
        validateMethodConstraintDefinitions(method);
        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        Class<?> beanClass = object != null ? object.getClass() : method.getDeclaringClass();

        // Resolve group sequences
        List<Set<Class<?>>> seqSteps = resolveExecutableGroupSequence(groupSet, method.getDeclaringClass());
        List<Set<Class<?>>> groupSteps = seqSteps != null ? seqSteps : Collections.singletonList(groupSet);
        Set<Class<?>> defaultGroup = Collections.singleton(Default.class);

        boolean cascadingDone = false;
        for (Set<Class<?>> stepGroups : groupSteps) {
            int beforeDirect = violations.size();
            boolean hasDirectViolations = false;
            List<Class<?>> paramTypes = Arrays.asList(method.getParameterTypes());
            PathImpl returnPath = PathImpl.createRootPath()
                    .append(new PathImpl.MethodNodeImpl(method.getName(), paramTypes))
                    .append(new PathImpl.ReturnValueNodeImpl());

            // Walk the type hierarchy to find method constraints (including inherited ones)
            // Per BV spec 4.5.5: return value constraints from all declarations add up
            for (Class<?> current : getTypeHierarchy(beanClass)) {
                ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
                if (beanMeta == null) {
                    continue;
                }

                for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
                    if (matchesMethod(methodMeta, method)) {
                        // Validate group conversion rules
                        validateGroupConversionsForExecutable(methodMeta, method.getName());

                        // Collect return value constraints from this declaration (direct)
                        // Pass declared return type so validator lookup works even when value is null
                        Class<?> declaredReturnType = method.getReturnType();
                        for (ConstraintMetadata constraint : methodMeta.returnValueConstraints()) {
                            if (matchesGroup(constraint, stepGroups)) {
                                //noinspection rawtypes
                                validateConstraint(object, beanClass, object, returnValue,
                                        constraint, returnPath, declaredReturnType, null, returnValue,
                                        (Set) violations, null);
                            }
                        }

                        // Validate container element constraints on return value
                        if (returnValue != null) {
                            boolean suppressCascading = methodMeta.returnValueCascading();
                            for (ContainerElementConstraint cec : methodMeta
                                    .returnValueContainerElementConstraints()) {
                                validateContainerElementConstraints(object, beanClass,
                                        object, returnValue, cec, returnPath, stepGroups,
                                        violations, null, returnValue, null, suppressCascading);
                            }
                        }

                        // Track direct violation count before cascading
                        if (violations.size() > beforeDirect) {
                            hasDirectViolations = true;
                        }

                        // Cascading return value — only cascade once even if multiple
                        // declarations have @Valid (they are OR'ed per spec 4.5.5)
                        if (!cascadingDone && methodMeta.returnValueCascading() && returnValue != null) {
                            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                            Set<Class<?>> cascadeGroups = seqSteps != null ? defaultGroup : stepGroups;
                            cascadeValidation(object, beanClass, returnValue, returnPath,
                                    cascadeGroups, violations, visited,
                                    methodMeta.returnValueGroupConversions(),
                                    null, returnValue, null);
                            cascadingDone = true;
                        }
                        break; // Only one method per class can match
                    }
                }
            }
            // Short-circuit: only based on direct constraint violations, not cascaded ones
            if (seqSteps != null && hasDirectViolations) {
                break;
            }
        }

        return violations;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<ConstraintViolation<T>> validateConstructorParameters(Constructor<? extends T> constructor,
            Object[] parameterValues, Class<?>... groups) {
        if (parameterValues == null) {
            throw new IllegalArgumentException("Parameter values must not be null");
        }
        validateMethodConstraintDefinitions(constructor);
        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        Class<?> beanClass = constructor.getDeclaringClass();

        ConstrainedBeanMetadata beanMeta = metadata.getBean(beanClass.getName());
        if (beanMeta == null) {
            return violations;
        }

        // Resolve group sequences
        List<Set<Class<?>>> seqSteps = resolveExecutableGroupSequence(groupSet, beanClass);
        List<Set<Class<?>>> groupSteps = seqSteps != null ? seqSteps : Collections.singletonList(groupSet);
        // Per spec: for cascaded validation during group sequence, Default is propagated
        Set<Class<?>> defaultGroup = Collections.singleton(Default.class);

        Set<Integer> cascadedParams = new HashSet<>();
        for (Set<Class<?>> stepGroups : groupSteps) {
            int beforeDirect = violations.size();
            boolean hasDirectViolations = false;
            for (ConstrainedMethodMetadata methodMeta : beanMeta.constructors()) {
                if (matchesConstructor(methodMeta, constructor)) {
                    // Validate group conversion rules
                    validateGroupConversionsForExecutable(methodMeta,
                            constructor.getDeclaringClass().getSimpleName());

                    List<Class<?>> paramTypes = Arrays.asList(constructor.getParameterTypes());
                    PathImpl ctorPath = PathImpl.createRootPath()
                            .append(new PathImpl.ConstructorNodeImpl(
                                    constructor.getDeclaringClass().getSimpleName(), paramTypes));

                    // Cross-parameter constraints (direct)
                    List<String> crossParamNames = methodMeta.crossParameterConstraints().isEmpty()
                            ? null
                            : parameterNameProvider.getParameterNames(constructor);
                    for (ConstraintMetadata constraint : methodMeta.crossParameterConstraints()) {
                        if (matchesGroup(constraint, stepGroups)) {
                            PathImpl crossParamPath = ctorPath.append(new PathImpl.CrossParameterNodeImpl());
                            //noinspection rawtypes
                            validateConstraint(null, beanClass, null, parameterValues,
                                    constraint, crossParamPath, null, parameterValues, null,
                                    (Set) violations, crossParamNames);
                        }
                    }

                    // Direct parameter constraints (no cascading yet)
                    List<String> ctorParamNames = parameterNameProvider.getParameterNames(constructor);
                    validateMethodParametersDirect(null, beanClass, methodMeta, parameterValues,
                            ctorPath, stepGroups, violations, ctorParamNames);

                    // Track direct violation count for short-circuiting decision
                    hasDirectViolations = violations.size() > beforeDirect;

                    // Cascaded parameter validation — cascade each parameter only once
                    Set<Class<?>> cascadeGroups = seqSteps != null ? defaultGroup : stepGroups;
                    validateMethodParametersCascadingOnce(null, beanClass, methodMeta, parameterValues,
                            ctorPath, cascadeGroups, violations, ctorParamNames, cascadedParams);
                    break;
                }
            }
            // Short-circuit: only based on direct constraint violations, not cascaded ones
            if (seqSteps != null && hasDirectViolations) {
                break;
            }
        }

        return violations;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(Constructor<? extends T> constructor,
            T createdObject, Class<?>... groups) {
        validateMethodConstraintDefinitions(constructor);
        Set<Class<?>> groupSet = resolveGroups(groups);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        Class<?> beanClass = constructor.getDeclaringClass();

        ConstrainedBeanMetadata beanMeta = metadata.getBean(beanClass.getName());
        if (beanMeta == null) {
            return violations;
        }

        // Resolve group sequences
        List<Set<Class<?>>> seqSteps = resolveExecutableGroupSequence(groupSet, beanClass);
        List<Set<Class<?>>> groupSteps = seqSteps != null ? seqSteps : Collections.singletonList(groupSet);
        Set<Class<?>> defaultGroup = Collections.singleton(Default.class);

        boolean cascadingDone = false;
        for (Set<Class<?>> stepGroups : groupSteps) {
            int beforeDirect = violations.size();
            boolean hasDirectViolations = false;
            for (ConstrainedMethodMetadata methodMeta : beanMeta.constructors()) {
                if (matchesConstructor(methodMeta, constructor)) {
                    // Validate group conversion rules
                    validateGroupConversionsForExecutable(methodMeta,
                            constructor.getDeclaringClass().getSimpleName());

                    List<Class<?>> paramTypes = Arrays.asList(constructor.getParameterTypes());
                    PathImpl returnPath = PathImpl.createRootPath()
                            .append(new PathImpl.ConstructorNodeImpl(
                                    constructor.getDeclaringClass().getSimpleName(), paramTypes))
                            .append(new PathImpl.ReturnValueNodeImpl());

                    // Direct return value constraints
                    for (ConstraintMetadata constraint : methodMeta.returnValueConstraints()) {
                        if (matchesGroup(constraint, stepGroups)) {
                            // For constructor: rootBean=null per spec, leafBean=createdObject
                            //noinspection rawtypes
                            validateConstraint(null, beanClass,
                                    createdObject, createdObject,
                                    constraint, returnPath, null, null, createdObject, (Set) violations,
                                    null);
                        }
                    }

                    // Validate container element constraints on constructor return value
                    if (createdObject != null) {
                        boolean suppressCascading = methodMeta.returnValueCascading();
                        for (ContainerElementConstraint cec : methodMeta
                                .returnValueContainerElementConstraints()) {
                            validateContainerElementConstraints(null, beanClass,
                                    null, createdObject, cec, returnPath, stepGroups,
                                    violations, null, createdObject, null, suppressCascading);
                        }
                    }

                    // Track direct violation count before cascading
                    hasDirectViolations = violations.size() > beforeDirect;

                    // Cascading return value — only cascade once to avoid duplicates
                    if (!cascadingDone && methodMeta.returnValueCascading() && createdObject != null) {
                        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                        Set<Class<?>> cascadeGroups = seqSteps != null ? defaultGroup : stepGroups;
                        cascadeValidation(createdObject, beanClass, createdObject, returnPath,
                                cascadeGroups, violations, visited,
                                methodMeta.returnValueGroupConversions(),
                                null, createdObject, null);
                        cascadingDone = true;
                    }
                    break;
                }
            }
            // Short-circuit: only based on direct constraint violations, not cascaded ones
            if (seqSteps != null && hasDirectViolations) {
                break;
            }
        }

        return violations;
    }

    @SuppressWarnings("unchecked")
    private <T> void validateMethodParametersDirect(Object rootBean, Class<?> rootBeanClass,
            ConstrainedMethodMetadata methodMeta, Object[] parameterValues,
            PathImpl methodPath, Set<Class<?>> groups,
            Set<ConstraintViolation<T>> violations, List<String> runtimeParamNames) {
        var params = methodMeta.parameters();
        for (int i = 0; i < params.size() && i < parameterValues.length; i++) {
            var paramMeta = params.get(i);
            String paramName = (runtimeParamNames != null && i < runtimeParamNames.size())
                    ? runtimeParamNames.get(i)
                    : paramMeta.name();
            PathImpl paramPath = methodPath.append(
                    new PathImpl.ParameterNodeImpl(paramName, paramMeta.index()));

            for (ConstraintMetadata constraint : paramMeta.constraints()) {
                if (matchesGroup(constraint, groups)) {
                    //noinspection rawtypes
                    validateConstraint(rootBean, rootBeanClass,
                            rootBean, parameterValues[i],
                            constraint, paramPath, null, parameterValues, null, (Set) violations,
                            null);
                }
            }

            // Validate container element constraints on parameters
            if (parameterValues[i] != null) {
                for (ContainerElementConstraint cec : paramMeta.containerElementConstraints()) {
                    validateContainerElementConstraints(rootBean, rootBeanClass,
                            rootBean, parameterValues[i], cec, paramPath, groups,
                            violations, parameterValues, null, null, false);
                }
            }
        }
    }

    /**
     * Cascades parameter validation, but only for parameters that haven't been cascaded yet.
     * This prevents duplicate violations when the same parameter has @Valid from multiple
     * hierarchy levels or across group sequence steps.
     */
    private <T> void validateMethodParametersCascadingOnce(Object rootBean, Class<?> rootBeanClass,
            ConstrainedMethodMetadata methodMeta, Object[] parameterValues,
            PathImpl methodPath, Set<Class<?>> cascadeGroups,
            Set<ConstraintViolation<T>> violations, List<String> runtimeParamNames,
            Set<Integer> alreadyCascaded) {
        var params = methodMeta.parameters();
        for (int i = 0; i < params.size() && i < parameterValues.length; i++) {
            var paramMeta = params.get(i);
            if (paramMeta.cascading() && parameterValues[i] != null && alreadyCascaded.add(i)) {
                String paramName = (runtimeParamNames != null && i < runtimeParamNames.size())
                        ? runtimeParamNames.get(i)
                        : paramMeta.name();
                PathImpl paramPath = methodPath.append(
                        new PathImpl.ParameterNodeImpl(paramName, paramMeta.index()));
                Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                cascadeValidation(rootBean, rootBeanClass, parameterValues[i], paramPath,
                        cascadeGroups, violations, visited, paramMeta.groupConversions(),
                        parameterValues, null, null);
            }
        }
    }

    // --- Optional unwrapping ---

    /**
     * @param declaredType null if type unchanged
     */
    private record UnwrappedValue(Object value, Class<?> declaredType) {
    }

    private static UnwrappedValue unwrapOptionalValue(Object value) {
        if (value instanceof OptionalInt opt) {
            return new UnwrappedValue(opt.isPresent() ? opt.getAsInt() : null, Integer.class);
        } else if (value instanceof OptionalLong opt) {
            return new UnwrappedValue(opt.isPresent() ? opt.getAsLong() : null, Long.class);
        } else if (value instanceof OptionalDouble opt) {
            return new UnwrappedValue(opt.isPresent() ? opt.getAsDouble() : null, Double.class);
        } else if (value instanceof Optional<?> opt) {
            Object unwrapped = opt.orElse(null);
            Class<?> type = unwrapped != null ? unwrapped.getClass() : Object.class;
            return new UnwrappedValue(unwrapped, type);
        }
        return null;
    }

    // --- Utility methods ---

    private Set<Class<?>> resolveGroups(Class<?>... groups) {
        if (groups == null) {
            throw new IllegalArgumentException("Groups must not be null");
        }
        Set<Class<?>> result = new LinkedHashSet<>();
        if (groups.length == 0) {
            result.add(Default.class);
        } else {
            for (Class<?> group : groups) {
                if (group == null) {
                    throw new IllegalArgumentException("Group must not be null");
                }
                // Expand group inheritance: if group extends other interfaces, include them
                Set<Class<?>> expanded = expandGroupWithInheritance(group);
                result.addAll(expanded);
            }
        }
        return result;
    }

    /**
     * Expands a group by including all groups inherited via interface extension.
     * For example, if group All extends PreManufacturing, PostManufacturing,
     * then validating with All.class should also validate constraints in those groups.
     */
    private Set<Class<?>> expandGroupWithInheritance(Class<?> group) {
        Set<Class<?>> expanded = new LinkedHashSet<>();
        collectGroupHierarchy(group, expanded);
        return expanded;
    }

    private void collectGroupHierarchy(Class<?> group, Set<Class<?>> collected) {
        if (group == null || !collected.add(group)) {
            return;
        }
        for (Class<?> parent : group.getInterfaces()) {
            collectGroupHierarchy(parent, collected);
        }
    }

    private Class<?>[] getGroupSequenceFromInterface(Class<?> group) {
        if (!group.isInterface()) {
            return null; // @GroupSequence on classes is handled by resolveGroupSequenceWithMode
        }

        // Check build-time metadata first
        List<String> sequence = metadata.interfaceGroupSequences().get(group.getName());
        if (sequence != null) {
            Class<?>[] result = new Class<?>[sequence.size()];
            for (int i = 0; i < sequence.size(); i++) {
                result[i] = loadClass(sequence.get(i));
            }
            return result;
        }

        // All @GroupSequence on interfaces is discovered at build time by scanInterfaceGroupSequences()
        return null;
    }

    private void detectGroupSequenceCycle(Class<?> group, Set<Class<?>> visited) {
        if (!visited.add(group)) {
            throw new GroupDefinitionException(
                    "Cyclic group sequence detected involving " + group.getName());
        }
        Class<?>[] sequence = getGroupSequenceFromInterface(group);
        if (sequence != null) {
            for (Class<?> seqGroup : sequence) {
                detectGroupSequenceCycle(seqGroup, visited);
            }
        }
        visited.remove(group);
    }

    private List<GroupSequenceStep> resolveGroupSequenceWithMode(ConstrainedBeanMetadata beanMeta, Set<Class<?>> groups) {
        if (beanMeta.groupSequence() != null) {
            // Validate the group sequence definition
            validateGroupSequence(beanMeta);

            // If the bean defines @GroupSequence, use it (only when Default group is requested)
            boolean containsDefault = groups.contains(Default.class);
            if (containsDefault) {
                // Collect non-Default groups that were also requested
                Set<Class<?>> otherGroups = new LinkedHashSet<>();
                for (Class<?> g : groups) {
                    if (g != Default.class) {
                        otherGroups.add(g);
                    }
                }

                List<GroupSequenceStep> steps = new ArrayList<>();
                for (Set<Class<?>> groupStep : buildGroupSequenceSteps(beanMeta)) {
                    steps.add(new GroupSequenceStep(groupStep, true));
                }
                // Non-Default groups are NOT sequenced — always evaluated
                if (!otherGroups.isEmpty()) {
                    steps.add(new GroupSequenceStep(otherGroups, false));
                }
                return steps;
            }
        }
        List<GroupSequenceStep> result = new ArrayList<>();
        result.add(new GroupSequenceStep(groups, false));
        return result;
    }

    private final Set<String> validatedGroupSequences = ConcurrentHashMap.newKeySet();

    private void validateGroupSequence(ConstrainedBeanMetadata beanMeta) {
        if (!validatedGroupSequences.add(beanMeta.className())) {
            return; // already validated
        }
        List<String> sequence = beanMeta.groupSequence();
        if (sequence == null) {
            return;
        }

        // Default.class must not appear in @GroupSequence
        for (String groupName : sequence) {
            if (Default.class.getName().equals(groupName)) {
                throw new GroupDefinitionException(
                        "Default.class must not be part of a @GroupSequence on " + beanMeta.className());
            }
        }

        // The bean's own class must appear in the sequence (it represents the implicit Default group)
        boolean containsSelf = false;
        for (String groupName : sequence) {
            if (groupName.equals(beanMeta.className())) {
                containsSelf = true;
                break;
            }
        }
        if (!containsSelf) {
            throw new GroupDefinitionException(
                    "The @GroupSequence on " + beanMeta.className()
                            + " must contain the class itself as implicit Default group");
        }

        // Check for cyclic group sequences — groups in the sequence (other than the
        // defining class) must not themselves define a @GroupSequence
        for (String groupName : sequence) {
            if (groupName.equals(beanMeta.className())) {
                continue; // Skip the bean class itself — it represents Default
            }
            ConstrainedBeanMetadata groupMeta = metadata.getBean(groupName);
            if (groupMeta != null && groupMeta.groupSequence() != null) {
                throw new GroupDefinitionException(
                        "Group '" + groupName + "' in @GroupSequence of " + beanMeta.className()
                                + " must not itself define a @GroupSequence (cyclic)");
            }
        }
    }

    private boolean matchesGroup(ConstraintMetadata constraint, Set<Class<?>> requestedGroups) {
        List<String> constraintGroups = constraint.groups();
        if (constraintGroups == null || constraintGroups.isEmpty()) {
            // Default group — groups were already expanded via inheritance in resolveGroups()
            return requestedGroups.contains(Default.class);
        }
        for (String groupName : constraintGroups) {
            for (Class<?> requestedGroup : requestedGroups) {
                if (requestedGroup.getName().equals(groupName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Validates group conversion rules for a bean class:
     * - @ConvertGroup must only be used with @Valid
     * - The "from" group must not be a @GroupSequence
     */
    private void validateGroupConversionsForBean(Class<?> beanClass) {
        // Check for duplicate @ConvertGroup "from" groups using build-time results
        if (!metadata.convertGroupValidatedBeans().contains(beanClass.getName())) {
            String error = metadata.convertGroupDuplicateErrors().get(beanClass.getName());
            if (error != null) {
                throw new ConstraintDeclarationException(error);
            }
        }

        for (Class<?> current : getTypeHierarchy(beanClass)) {
            ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
            if (beanMeta == null) {
                continue;
            }

            // Check fields
            for (ConstrainedFieldMetadata fieldMeta : beanMeta.fields()) {
                String context = "field '" + fieldMeta.name() + "' of " + current.getName();
                validateGroupConversionRequiresValid(fieldMeta.groupConversions(), fieldMeta.cascading(), context);
                validateGroupConversionFrom(fieldMeta.groupConversions(), context);
                // Check container element group conversions
                for (ContainerElementConstraint cec : fieldMeta.containerElementConstraints()) {
                    validateContainerElementGroupConversions(cec, context);
                }
            }

            // Check getters
            for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
                if (methodMeta.getter()) {
                    validateGroupConversionRequiresValid(methodMeta.returnValueGroupConversions(),
                            methodMeta.returnValueCascading(),
                            "property '" + getPropertyNameFromGetter(methodMeta.name()) + "' of "
                                    + current.getName());
                    validateGroupConversionFrom(methodMeta.returnValueGroupConversions(),
                            "property '" + getPropertyNameFromGetter(methodMeta.name()) + "' of "
                                    + current.getName());
                    // Check container element group conversions on return value
                    for (ContainerElementConstraint cec : methodMeta
                            .returnValueContainerElementConstraints()) {
                        validateContainerElementGroupConversions(cec,
                                "property '" + getPropertyNameFromGetter(methodMeta.name()) + "' of "
                                        + current.getName());
                    }
                }
            }
        }
    }

    private void validateGroupConversionsForExecutable(ConstrainedMethodMetadata methodMeta, String context) {
        // Check parameters
        for (ConstrainedParameterMetadata paramMeta : methodMeta.parameters()) {
            validateGroupConversionRequiresValid(paramMeta.groupConversions(), paramMeta.cascading(),
                    "parameter '" + paramMeta.name() + "' of " + context);
            validateGroupConversionFrom(paramMeta.groupConversions(),
                    "parameter '" + paramMeta.name() + "' of " + context);
            // Check container element group conversions on parameters
            for (ContainerElementConstraint cec : paramMeta.containerElementConstraints()) {
                validateContainerElementGroupConversions(cec,
                        "parameter '" + paramMeta.name() + "' of " + context);
            }
        }
        // Check return value
        validateGroupConversionRequiresValid(methodMeta.returnValueGroupConversions(),
                methodMeta.returnValueCascading(), "return value of " + context);
        validateGroupConversionFrom(methodMeta.returnValueGroupConversions(), "return value of " + context);
        // Check container element group conversions on return value
        for (ContainerElementConstraint cec : methodMeta.returnValueContainerElementConstraints()) {
            validateContainerElementGroupConversions(cec, "return value of " + context);
        }
    }

    private void validateGroupConversionRequiresValid(Map<String, String> conversions, boolean cascading,
            String context) {
        if (conversions != null && !conversions.isEmpty() && !cascading) {
            throw new ConstraintDeclarationException(
                    "@ConvertGroup requires @Valid on " + context);
        }
    }

    private void validateGroupConversionFrom(Map<String, String> conversions, String context) {
        if (conversions == null || conversions.isEmpty()) {
            return;
        }
        for (String fromGroup : conversions.keySet()) {
            Class<?> fromClass = loadClass(fromGroup);
            if (fromClass != null) {
                // Check if the "from" group is a @GroupSequence
                Class<?>[] sequence = getGroupSequenceFromInterface(fromClass);
                if (sequence != null) {
                    throw new ConstraintDeclarationException(
                            "@ConvertGroup from group must not be a @GroupSequence: " + fromGroup + " on " + context);
                }
            }
        }
    }

    /**
     * Validates group conversion rules on container element type arguments.
     * Per BV spec: @ConvertGroup on container element requires @Valid,
     * must not have duplicate "from" groups, and "from" must not be a @GroupSequence.
     */
    private void validateContainerElementGroupConversions(ContainerElementConstraint cec, String context) {
        Map<String, String> conversions = cec.groupConversions();
        if (conversions != null && !conversions.isEmpty()) {
            validateGroupConversionRequiresValid(conversions, cec.cascading(),
                    "container element of " + context);
            validateGroupConversionFrom(conversions, "container element of " + context);
        }
        // Recursively validate nested container element group conversions
        List<ContainerElementConstraint> nested = cec.nestedContainerElements();
        if (nested != null) {
            for (ContainerElementConstraint nestedCec : nested) {
                validateContainerElementGroupConversions(nestedCec, context);
            }
        }
    }

    private Set<Class<?>> convertGroups(Set<Class<?>> groups, Map<String, String> conversions) {
        if (conversions == null || conversions.isEmpty()) {
            return groups;
        }
        Set<Class<?>> converted = new LinkedHashSet<>();
        for (Class<?> group : groups) {
            String target = conversions.get(group.getName());
            if (target != null) {
                try {
                    converted.add(Class.forName(target));
                } catch (ClassNotFoundException e) {
                    throw new ValidationException("Cannot load group class: " + target, e);
                }
            } else {
                converted.add(group);
            }
        }
        return converted;
    }

    private Class<?> loadClass(String className) {
        return ValidationUtils.loadClass(className);
    }

    private Object getFieldValue(Object bean, Class<?> beanClass, String fieldName) {
        BeanPropertyAccessor accessor = QuarkusValidationProvider.getAccessor(beanClass.getName());
        if (accessor != null) {
            return accessor.getFieldValue(bean, fieldName);
        }
        if (QuarkusValidationProvider.isExtensionMode()) {
            throw new ValidationException("No build-time accessor for field '" + fieldName
                    + "' on " + beanClass.getName()
                    + ". This is a bug in the Quarkus Bean Validation extension.");
        }
        // Reflection fallback for standalone (non-Quarkus) usage
        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(bean);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new ValidationException("Cannot access field: " + fieldName, e);
            }
        }
        throw new ValidationException("Field not found: " + fieldName + " on " + beanClass.getName());
    }

    private Object getPropertyValue(Object bean, Class<?> beanClass, String methodName) {
        BeanPropertyAccessor accessor = QuarkusValidationProvider.getAccessor(beanClass.getName());
        if (accessor != null) {
            return accessor.getPropertyValue(bean, methodName);
        }
        if (QuarkusValidationProvider.isExtensionMode()) {
            throw new ValidationException("No build-time accessor for getter '" + methodName
                    + "' on " + beanClass.getName()
                    + ". This is a bug in the Quarkus Bean Validation extension.");
        }
        // Reflection fallback for standalone (non-Quarkus) usage
        for (Class<?> current = bean.getClass(); current != null
                && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(bean);
            } catch (NoSuchMethodException e) {
                // Continue to superclass
            } catch (Exception e) {
                throw new ValidationException("Cannot invoke getter: " + methodName, e);
            }
        }
        // Also check interfaces (for default methods)
        for (Class<?> iface : bean.getClass().getInterfaces()) {
            try {
                Method method = iface.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(bean);
            } catch (NoSuchMethodException e) {
                // Continue
            } catch (Exception e) {
                throw new ValidationException("Cannot invoke getter: " + methodName, e);
            }
        }
        throw new ValidationException("Cannot invoke getter: " + methodName);
    }

    static String getPropertyNameFromGetter(String methodName) {
        return ValidationUtils.getPropertyNameFromGetter(methodName);
    }

    private boolean propertyExistsOnType(Class<?> type, String propertyName) {
        for (Class<?> current : getTypeHierarchy(type)) {
            ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
            if (beanMeta != null && beanMeta.allPropertyNames() != null) {
                if (beanMeta.allPropertyNames().contains(propertyName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesMethod(ConstrainedMethodMetadata methodMeta, Method method) {
        if (!methodMeta.name().equals(method.getName())) {
            return false;
        }
        return matchesParameterTypes(methodMeta, method.getParameterTypes());
    }

    private boolean matchesConstructor(ConstrainedMethodMetadata methodMeta, Constructor<?> constructor) {
        if (!methodMeta.constructor()) {
            return false;
        }
        return matchesParameterTypes(methodMeta, constructor.getParameterTypes());
    }

    private boolean matchesParameterTypes(ConstrainedMethodMetadata methodMeta, Class<?>[] paramTypes) {
        List<String> metaParamTypes = methodMeta.parameterTypeNames();
        if (metaParamTypes.isEmpty()) {
            // Fall back to parameter count matching if type names not available
            return methodMeta.parameters().size() == paramTypes.length;
        }
        if (metaParamTypes.size() != paramTypes.length) {
            return false;
        }
        for (int i = 0; i < paramTypes.length; i++) {
            if (!metaParamTypes.get(i).equals(paramTypes[i].getName())) {
                return false;
            }
        }
        return true;
    }

    private String getMessageTemplate(ConstraintMetadata constraint) {
        String messageTemplate = (String) constraint.attributes().get("message");
        if (messageTemplate == null) {
            // Use pre-computed default message template from build time
            messageTemplate = constraint.defaultMessageTemplate();
        }
        if (messageTemplate == null) {
            messageTemplate = "{" + constraint.annotationClassName() + ".message}";
        }
        return messageTemplate;
    }

    private String interpolateMessage(String messageTemplate, Object value,
            ConstraintDescriptor<?> descriptor) {
        if (messageTemplate == null) {
            return "";
        }
        MessageInterpolator.Context context = new MessageInterpolatorContext(descriptor, value);
        try {
            return messageInterpolator.interpolate(messageTemplate, context);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Exception during message interpolation", e);
        }
    }

    @SuppressWarnings("unchecked")
    ConstraintDescriptor<?> createConstraintDescriptor(ConstraintMetadata constraint) {
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) loadClass(
                constraint.annotationClassName());
        return new ConstraintDescriptorImpl<>(
                annotationClass,
                constraint.attributes(),
                constraint.groups(),
                constraint.payload(),
                constraint.validatorClassName(),
                constraint.reportAsSingleViolation(),
                constraint.composingConstraints());
    }

    @SuppressWarnings("unchecked")
    private Annotation createAnnotationProxy(ConstraintMetadata constraint) {
        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) loadClass(
                constraint.annotationClassName());
        Map<String, Object> attributes = constraint.attributes();
        return QuarkusValidationProvider.createAnnotationLiteral(annotationType, attributes);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ConstraintValidator<?, ?> getOrCreateValidator(ConstraintMetadata constraint,
            Class<? extends ConstraintValidator<?, ?>> validatorClass) {
        ValidatorCacheKey cacheKey = new ValidatorCacheKey(constraint, validatorClass);
        ConstraintValidator<?, ?> cached = validatorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ConstraintValidator validator = constraintValidatorFactory.getInstance(validatorClass);
        if (validator == null) {
            throw new ValidationException(
                    "ConstraintValidatorFactory returned null for validator class: " + validatorClass.getName());
        }

        Annotation annotationProxy = annotationProxyCache.computeIfAbsent(constraint,
                new Function<ConstraintMetadata, Annotation>() {
                    @Override
                    public Annotation apply(ConstraintMetadata cm) {
                        return createAnnotationProxy(cm);
                    }
                });
        if (annotationProxy != null) {
            try {
                validator.initialize(annotationProxy);
            } catch (ClassCastException e) {
                throw new ConstraintDefinitionException(
                        "Attribute type mismatch in constraint definition: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new ValidationException("Exception initializing constraint validator: " + e.getMessage(), e);
            }
        }

        validatorCache.put(cacheKey, validator);
        return validator;
    }

    /**
     * Resolves @OverridesAttribute mappings from a composed constraint annotation
     * using pre-computed build-time mappings.
     * Returns a map: composing annotation FQCN + ":" + constraintIndex -> (attributeName -> value)
     */
    private Map<String, Map<String, Object>> resolveOverridesAttributes(ConstraintMetadata composedConstraint) {
        Map<String, Map<String, String>> mappings = composedConstraint.overridesAttributes();
        if (mappings.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> composedAttributes = composedConstraint.attributes();
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : mappings.entrySet()) {
            String key = entry.getKey();
            Map<String, String> attrMappings = entry.getValue();
            Map<String, Object> overrideValues = new HashMap<>();

            for (Map.Entry<String, String> mapping : attrMappings.entrySet()) {
                String targetName = mapping.getKey();
                String sourceName = mapping.getValue();
                Object value = composedAttributes.get(sourceName);
                if (value != null) {
                    overrideValues.put(targetName, value);
                }
            }

            if (!overrideValues.isEmpty()) {
                result.put(key, overrideValues);
            }
        }

        return result;
    }

    private Map<String, Object> getAttributeOverridesForComposing(
            Map<String, Map<String, Object>> overridesMap,
            String composingAnnotationClassName, int composingIndex) {
        if (overridesMap.isEmpty()) {
            return Collections.emptyMap();
        }
        // Try with the specific index first
        Map<String, Object> overrides = overridesMap.get(composingAnnotationClassName + ":" + composingIndex);
        if (overrides == null) {
            // Try with index -1 (default = "apply to first/only constraint of this type")
            overrides = overridesMap.get(composingAnnotationClassName + ":-1");
        }
        return overrides != null ? overrides : Collections.emptyMap();
    }

    /**
     * Validates that override attribute values have types compatible with the target
     * constraint annotation's attribute types, using pre-computed type categories
     * from build time (no reflection needed).
     * Per BV spec, @OverridesAttribute source and target must have the same type.
     */
    private static void validateOverrideAttributeTypes(String composingAnnotationClassName,
            Map<String, Object> overrideValues, Map<String, String> attributeTypes) {
        if (overrideValues.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> override : overrideValues.entrySet()) {
            String attrName = override.getKey();
            Object overrideValue = override.getValue();
            if (overrideValue == null) {
                continue;
            }
            String typeCategory = attributeTypes.get(attrName);
            if (typeCategory == null) {
                throw new ConstraintDefinitionException(
                        "The composing constraint " + composingAnnotationClassName
                                + " does not have attribute '" + attrName + "'");
            }
            if (!isOverrideTypeCompatible(overrideValue, typeCategory)) {
                throw new ConstraintDefinitionException(
                        "The overriding type " + overrideValue.getClass().getName()
                                + " is not compatible with the expected type category '"
                                + typeCategory + "' for attribute '" + attrName + "' of "
                                + composingAnnotationClassName);
            }
        }
    }

    private static boolean isOverrideTypeCompatible(Object value, String typeCategory) {
        return switch (typeCategory) {
            case "int", "long", "short", "byte", "float", "double", "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "char" -> value instanceof Character;
            case "String" -> value instanceof String;
            case "Class" -> value instanceof String || value instanceof Class;
            case "Class[]" -> value instanceof List || value instanceof Class[];
            case "array" -> value instanceof List || value.getClass().isArray();
            case "enum" -> value instanceof String || value instanceof Enum;
            default -> true;
        };
    }

    /**
     * Returns the path to pass to the TraversableResolver.
     * Per BV spec, when the traversable object is the root bean itself, the path
     * should contain a single bean node with a null name (representing the root).
     * An empty path (no nodes) is replaced with a single-node root bean path.
     */
    private Path traversableResolverPath(Path pathToTraversableObject) {
        if (!pathToTraversableObject.iterator().hasNext()) {
            return PathImpl.createPathForBean();
        }
        return pathToTraversableObject;
    }

    /**
     * Checks if a property is reachable according to the TraversableResolver.
     * Per BV spec, if the resolver throws an exception, it is wrapped in ValidationException.
     */
    private boolean isReachable(Object bean, Path.Node traversableProperty,
            Class<?> rootBeanType, Path pathToTraversableObject,
            ElementType elementType) {
        try {
            return traversableResolver.isReachable(bean, traversableProperty,
                    rootBeanType, traversableResolverPath(pathToTraversableObject), elementType);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Exception in TraversableResolver.isReachable()", e);
        }
    }

    /**
     * Checks if a property is cascadable according to the TraversableResolver.
     * Per BV spec, if the resolver throws an exception, it is wrapped in ValidationException.
     */
    private boolean isCascadable(Object bean, Path.Node traversableProperty,
            Class<?> rootBeanType, Path pathToTraversableObject,
            ElementType elementType) {
        try {
            return traversableResolver.isCascadable(bean, traversableProperty,
                    rootBeanType, traversableResolverPath(pathToTraversableObject), elementType);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Exception in TraversableResolver.isCascadable()", e);
        }
    }

    private PathImpl.NodeImpl getLastNode(PathImpl path) {
        PathImpl.NodeImpl last = null;
        for (Path.Node node : path) {
            last = (PathImpl.NodeImpl) node;
        }
        return last;
    }

    /**
     * Validates that a constraint annotation conforms to the Bean Validation specification.
     * All constraint definitions are validated at build time via Jandex. Invalid constraints
     * have their error messages pre-recorded for runtime throwing.
     */
    private void validateConstraintDefinition(String annotationClassName) {
        if (validatedConstraintDefinitions.contains(annotationClassName)) {
            return;
        }
        validatedConstraintDefinitions.add(annotationClassName);

        // Check if build-time validation found this constraint to be invalid
        String error = metadata.invalidConstraintDefinitions().get(annotationClassName);
        if (error != null) {
            throw new ConstraintDefinitionException(error);
        }
        String declarationError = metadata.invalidConstraintDeclarations().get(annotationClassName);
        if (declarationError != null) {
            throw new ConstraintDeclarationException(declarationError);
        }
    }

    private void validateMethodConstraintDefinitions(java.lang.reflect.Executable executable) {
        // Find method metadata and validate constraint definitions from it
        ConstrainedMethodMetadata methodMeta = findMethodMetadata(executable);
        if (methodMeta != null) {
            // Validate return value constraints
            for (ConstraintMetadata constraint : methodMeta.returnValueConstraints()) {
                validateConstraintDefinition(constraint.annotationClassName());
            }
            // Validate parameter constraints
            for (ConstrainedParameterMetadata paramMeta : methodMeta.parameters()) {
                for (ConstraintMetadata constraint : paramMeta.constraints()) {
                    validateConstraintDefinition(constraint.annotationClassName());
                }
            }
            // Validate constraint target using metadata attributes
            validateConstraintTargetForExecutable(executable, methodMeta);
        }

        // Validate method constraint inheritance rules (BV spec sections 4.5.5)
        if (executable instanceof Method method) {
            validateMethodConstraintInheritance(method);
        }
    }

    private ConstrainedMethodMetadata findMethodMetadata(java.lang.reflect.Executable executable) {
        Class<?> declaringClass = executable.getDeclaringClass();
        ConstrainedBeanMetadata beanMeta = metadata.getBean(declaringClass.getName());
        if (beanMeta == null) {
            return null;
        }
        if (executable instanceof Constructor<?> constructor) {
            for (ConstrainedMethodMetadata m : beanMeta.constructors()) {
                if (matchesConstructor(m, constructor)) {
                    return m;
                }
            }
        } else {
            for (ConstrainedMethodMetadata m : beanMeta.methods()) {
                if (executable instanceof Method method && matchesMethod(m, method)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Validates that method constraint declarations in the type hierarchy are consistent
     * per BV spec section 4.5.5:
     * - Parameter constraints must NOT be added in subtypes
     * - @Valid on parameters must NOT be added in subtypes
     * - Return value @Valid must be consistent (if any declares it, all must)
     * - @ConvertGroup on parameters/return values must be consistent
     */
    private void validateMethodConstraintInheritance(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        String methodName = method.getName();
        Class<?>[] paramTypes = method.getParameterTypes();
        int paramCount = paramTypes.length;

        // Collect all declarations of this method from superclasses and interfaces
        List<MethodDeclarationInfo> declarations = new ArrayList<>();
        collectMethodDeclarations(declaringClass, methodName, paramTypes, declarations, new HashSet<>());

        if (declarations.size() <= 1) {
            return; // No inheritance to check
        }

        // Check parameter constraints and cascading across the hierarchy
        for (int i = 0; i < paramCount; i++) {
            // Separate declarations into those with and without parameter constraints/cascading
            List<MethodDeclarationInfo> withParamConstraints = new ArrayList<>();
            List<MethodDeclarationInfo> withoutParamConstraints = new ArrayList<>();
            List<MethodDeclarationInfo> withParamCascading = new ArrayList<>();
            List<MethodDeclarationInfo> withoutParamCascading = new ArrayList<>();

            for (MethodDeclarationInfo decl : declarations) {
                if (decl.hasParameterConstraints(i)) {
                    withParamConstraints.add(decl);
                } else {
                    withoutParamConstraints.add(decl);
                }
                if (decl.hasParameterCascading(i)) {
                    withParamCascading.add(decl);
                } else {
                    withoutParamCascading.add(decl);
                }
            }

            // Per BV spec 4.5.5: parameter constraints must be declared in at most
            // one place in the type hierarchy
            if (withParamConstraints.size() > 1) {
                throw new ConstraintDeclarationException(
                        "Parameter " + i + " has constraints declared in multiple types in the hierarchy for method "
                                + methodName);
            }

            // Check: if a child has parameter constraints but a parent does not, invalid
            for (MethodDeclarationInfo child : withParamConstraints) {
                for (MethodDeclarationInfo parent : withoutParamConstraints) {
                    if (parent.declaringClass.isAssignableFrom(child.declaringClass)
                            && parent.declaringClass != child.declaringClass) {
                        throw new ConstraintDeclarationException(
                                "Parameter constraints must not be added in subtypes for method " +
                                        methodName + " parameter " + i);
                    }
                }
            }

            // Check: parallel types (interfaces or interface+class) with different constraint state
            for (MethodDeclarationInfo a : withParamConstraints) {
                for (MethodDeclarationInfo b : withoutParamConstraints) {
                    if (!a.declaringClass.isAssignableFrom(b.declaringClass)
                            && !b.declaringClass.isAssignableFrom(a.declaringClass)) {
                        throw new ConstraintDeclarationException(
                                "Inconsistent parameter constraints across parallel types for method " +
                                        methodName + " parameter " + i);
                    }
                }
            }

            // Per BV spec 4.5.5: @Valid on parameters must be declared in at most
            // one place in the type hierarchy
            if (withParamCascading.size() > 1) {
                throw new ConstraintDeclarationException(
                        "@Valid on parameter " + i
                                + " is declared in multiple types in the hierarchy for method " + methodName);
            }

            // Same checks for @Valid cascading on parameters
            for (MethodDeclarationInfo child : withParamCascading) {
                for (MethodDeclarationInfo parent : withoutParamCascading) {
                    if (parent.declaringClass.isAssignableFrom(child.declaringClass)
                            && parent.declaringClass != child.declaringClass) {
                        throw new ConstraintDeclarationException(
                                "@Valid must not be added to parameters in subtypes for method " +
                                        methodName + " parameter " + i);
                    }
                }
            }

            for (MethodDeclarationInfo a : withParamCascading) {
                for (MethodDeclarationInfo b : withoutParamCascading) {
                    if (!a.declaringClass.isAssignableFrom(b.declaringClass)
                            && !b.declaringClass.isAssignableFrom(a.declaringClass)) {
                        throw new ConstraintDeclarationException(
                                "Inconsistent @Valid on parameters across parallel types for method " +
                                        methodName + " parameter " + i);
                    }
                }
            }
        }

        // Check @ConvertGroup consistency across hierarchy
        for (int i = 0; i < paramCount; i++) {
            Map<String, String> firstConversions = null;
            Class<?> firstClass = null;
            for (MethodDeclarationInfo decl : declarations) {
                Map<String, String> conversions = decl.getParameterGroupConversions(i);
                if (conversions.isEmpty()) {
                    continue;
                }
                if (firstConversions == null) {
                    firstConversions = conversions;
                    firstClass = decl.declaringClass;
                } else if (!Objects.equals(firstConversions, conversions)) {
                    throw new ConstraintDeclarationException(
                            "Inconsistent @ConvertGroup on parameter " + i + " for method " + methodName);
                }
            }
        }

        // Check return value @ConvertGroup consistency across parallel types.
        // Per BV spec 4.5.5: @ConvertGroup on return value from parallel super types
        // (e.g., two unrelated interfaces, or interface + non-implementing superclass)
        // must be identical. If one has @ConvertGroup and another doesn't, or they
        // have different conversions, throw ConstraintDeclarationException.
        {
            List<MethodDeclarationInfo> withReturnConversions = new ArrayList<>();
            List<MethodDeclarationInfo> withoutReturnConversions = new ArrayList<>();
            for (MethodDeclarationInfo decl : declarations) {
                Map<String, String> conversions = decl.getReturnValueGroupConversions();
                if (!conversions.isEmpty()) {
                    withReturnConversions.add(decl);
                } else {
                    withoutReturnConversions.add(decl);
                }
            }
            // Check: parallel type has @ConvertGroup but another parallel type doesn't
            for (MethodDeclarationInfo a : withReturnConversions) {
                for (MethodDeclarationInfo b : withoutReturnConversions) {
                    if (!a.declaringClass.isAssignableFrom(b.declaringClass)
                            && !b.declaringClass.isAssignableFrom(a.declaringClass)) {
                        throw new ConstraintDeclarationException(
                                "Inconsistent @ConvertGroup on return value across parallel types for method "
                                        + methodName);
                    }
                }
            }
            // Check: two parallel types both have @ConvertGroup but with different values
            for (int a = 0; a < withReturnConversions.size(); a++) {
                for (int b = a + 1; b < withReturnConversions.size(); b++) {
                    Class<?> ca = withReturnConversions.get(a).declaringClass;
                    Class<?> cb = withReturnConversions.get(b).declaringClass;
                    if (!ca.isAssignableFrom(cb) && !cb.isAssignableFrom(ca)
                            && !Objects.equals(withReturnConversions.get(a).getReturnValueGroupConversions(),
                                    withReturnConversions.get(b).getReturnValueGroupConversions())) {
                        throw new ConstraintDeclarationException(
                                "Inconsistent @ConvertGroup on return value across parallel types for method "
                                        + methodName);
                    }
                }
            }
        }

        // Check container element @ConvertGroup consistency on return value across parallel types
        {
            List<MethodDeclarationInfo> withCeConversions = new ArrayList<>();
            List<MethodDeclarationInfo> withoutCeConversions = new ArrayList<>();
            for (MethodDeclarationInfo decl : declarations) {
                Map<Integer, Map<String, String>> ceConversions = decl.getReturnValueContainerElementGroupConversions();
                if (!ceConversions.isEmpty()) {
                    withCeConversions.add(decl);
                } else {
                    withoutCeConversions.add(decl);
                }
            }
            // Check: parallel type has container element @ConvertGroup but another doesn't
            for (MethodDeclarationInfo a : withCeConversions) {
                for (MethodDeclarationInfo b : withoutCeConversions) {
                    if (!a.declaringClass.isAssignableFrom(b.declaringClass)
                            && !b.declaringClass.isAssignableFrom(a.declaringClass)) {
                        throw new ConstraintDeclarationException(
                                "Inconsistent container element @ConvertGroup on return value across parallel types for method "
                                        + methodName);
                    }
                }
            }
            // Check: two parallel types both have container element @ConvertGroup but with different values
            for (int a = 0; a < withCeConversions.size(); a++) {
                for (int b = a + 1; b < withCeConversions.size(); b++) {
                    Class<?> ca = withCeConversions.get(a).declaringClass;
                    Class<?> cb = withCeConversions.get(b).declaringClass;
                    if (!ca.isAssignableFrom(cb) && !cb.isAssignableFrom(ca)
                            && !Objects.equals(
                                    withCeConversions.get(a).getReturnValueContainerElementGroupConversions(),
                                    withCeConversions.get(b).getReturnValueContainerElementGroupConversions())) {
                        throw new ConstraintDeclarationException(
                                "Inconsistent container element @ConvertGroup on return value across parallel types for method "
                                        + methodName);
                    }
                }
            }
            // Check: duplicate "from" groups across container element conversions on return value
            for (MethodDeclarationInfo decl : declarations) {
                if (decl.hasReturnValueContainerElementDuplicateFromGroups()) {
                    throw new ConstraintDeclarationException(
                            "Duplicate 'from' group in container element @ConvertGroup on return value for method "
                                    + methodName);
                }
            }
        }

        // Per BV spec 4.5.5: return value @Valid must not be declared in both a
        // parent and child in the type hierarchy (parent-child relationship is invalid).
        // Parallel interfaces both declaring @Valid is OK (they are OR'ed).
        List<MethodDeclarationInfo> withReturnCascading = new ArrayList<>();
        for (MethodDeclarationInfo decl : declarations) {
            if (decl.methodMeta != null && decl.methodMeta.returnValueCascading()) {
                withReturnCascading.add(decl);
            }
        }
        for (int a = 0; a < withReturnCascading.size(); a++) {
            for (int b = a + 1; b < withReturnCascading.size(); b++) {
                Class<?> ca = withReturnCascading.get(a).declaringClass;
                Class<?> cb = withReturnCascading.get(b).declaringClass;
                if (ca.isAssignableFrom(cb) || cb.isAssignableFrom(ca)) {
                    throw new ConstraintDeclarationException(
                            "Return value must not be marked as cascaded (@Valid) in both parent and child "
                                    + "in the hierarchy for method " + methodName);
                }
            }
        }
    }

    /**
     * Validates that constraint target (validationAppliesTo) is valid for all constraints
     * on a bean's fields and class-level. RETURN_VALUE and PARAMETERS are not valid for
     * field-level or type-level constraints.
     */
    private void validateConstraintTargetForBeanValidation(Class<?> beanClass) {
        if (!validatedConstraintTargetBeans.add(beanClass.getName())) {
            return; // Already validated
        }
        for (Class<?> current : getTypeHierarchy(beanClass)) {
            ConstrainedBeanMetadata beanMeta = metadata.getBean(current.getName());
            if (beanMeta == null) {
                continue;
            }

            // Class-level constraints: RETURN_VALUE and PARAMETERS are invalid
            for (ConstraintMetadata constraint : beanMeta.classConstraints()) {
                // Validate constraint definition first (may throw ConstraintDefinitionException
                // for invalid defaults like validationAppliesTo != IMPLICIT)
                validateConstraintDefinition(constraint.annotationClassName());
                ConstraintTarget target = getExplicitConstraintTarget(constraint);
                if (target == ConstraintTarget.RETURN_VALUE || target == ConstraintTarget.PARAMETERS) {
                    throw new ConstraintDeclarationException(
                            "Constraint " + constraint.annotationClassName()
                                    + " on type " + current.getName()
                                    + " declares validationAppliesTo=" + target
                                    + " which is not valid for type-level constraints");
                }
            }

            // Field constraints: RETURN_VALUE and PARAMETERS are invalid
            for (ConstrainedFieldMetadata field : beanMeta.fields()) {
                for (ConstraintMetadata constraint : field.constraints()) {
                    validateConstraintDefinition(constraint.annotationClassName());
                    ConstraintTarget target = getExplicitConstraintTarget(constraint);
                    if (target == ConstraintTarget.RETURN_VALUE || target == ConstraintTarget.PARAMETERS) {
                        throw new ConstraintDeclarationException(
                                "Constraint " + constraint.annotationClassName()
                                        + " on field " + field.name()
                                        + " declares validationAppliesTo=" + target
                                        + " which is not valid for field-level constraints");
                    }
                }
            }
        }
    }

    private ConstraintTarget getExplicitConstraintTarget(ConstraintMetadata constraint) {
        Object vat = constraint.attributes().get("validationAppliesTo");
        if (vat != null) {
            return ConstraintTarget.valueOf(vat.toString());
        }
        return null;
    }

    private void validateConstraintTargetForExecutable(java.lang.reflect.Executable executable,
            ConstrainedMethodMetadata methodMeta) {
        boolean hasParams = executable.getParameterCount() > 0;
        boolean hasReturnValue = executable instanceof Method method
                && method.getReturnType() != void.class;
        boolean isConstructor = executable instanceof Constructor;

        // Check return value constraints
        for (ConstraintMetadata constraint : methodMeta.returnValueConstraints()) {
            ConstraintTarget target = getExplicitConstraintTarget(constraint);
            if (target != null) {
                validateConstraintTarget(target, constraint.annotationClassName(),
                        executable.getName(), hasParams, hasReturnValue, isConstructor);
            }
        }

        // Check cross-parameter constraints
        for (ConstraintMetadata constraint : methodMeta.crossParameterConstraints()) {
            ConstraintTarget target = getExplicitConstraintTarget(constraint);
            if (target != null) {
                validateConstraintTarget(target, constraint.annotationClassName(),
                        executable.getName(), hasParams, hasReturnValue, isConstructor);
            }
        }
    }

    private void validateConstraintTarget(ConstraintTarget target, String annotationClassName,
            String executableName, boolean hasParams, boolean hasReturnValue, boolean isConstructor) {
        if (target == ConstraintTarget.RETURN_VALUE) {
            if (!hasReturnValue && !isConstructor) {
                throw new ConstraintDeclarationException(
                        "Constraint " + annotationClassName
                                + " declares validationAppliesTo=RETURN_VALUE but method "
                                + executableName + " has no return value");
            }
        } else if (target == ConstraintTarget.PARAMETERS) {
            if (!hasParams) {
                throw new ConstraintDeclarationException(
                        "Constraint " + annotationClassName
                                + " declares validationAppliesTo=PARAMETERS but "
                                + executableName + " has no parameters");
            }
        } else if (target == ConstraintTarget.IMPLICIT) {
            if (isConstructor && hasParams) {
                throw new ConstraintDeclarationException(
                        "Constraint " + annotationClassName
                                + " with validationAppliesTo=IMPLICIT is ambiguous on constructor "
                                + executableName + " with parameters");
            }
            if (!isConstructor && hasParams && hasReturnValue) {
                throw new ConstraintDeclarationException(
                        "Constraint " + annotationClassName
                                + " with validationAppliesTo=IMPLICIT is ambiguous on method "
                                + executableName + " with parameters and return value");
            }
        }
    }

    private void collectMethodDeclarations(Class<?> clazz, String methodName, Class<?>[] paramTypes,
            List<MethodDeclarationInfo> declarations, Set<Class<?>> visited) {
        if (clazz == null || clazz == Object.class || !visited.add(clazz)) {
            return;
        }

        // Check this class's metadata
        ConstrainedBeanMetadata beanMeta = metadata.getBean(clazz.getName());
        if (beanMeta != null) {
            for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
                if (methodMeta.name().equals(methodName) && matchesParamTypes(methodMeta, paramTypes)) {
                    declarations.add(new MethodDeclarationInfo(clazz, methodMeta));
                }
            }
        }

        // Check if this class declares the method via reflection (for constraints directly on the method)
        // even if no metadata exists
        if (beanMeta == null) {
            try {
                var ignored = clazz.getDeclaredMethod(methodName, paramTypes);
                declarations.add(new MethodDeclarationInfo(clazz, null));
            } catch (NoSuchMethodException e) {
                // Method not declared in this class
            }
        }

        // Recurse into superclass and interfaces
        collectMethodDeclarations(clazz.getSuperclass(), methodName, paramTypes, declarations, visited);
        for (Class<?> iface : clazz.getInterfaces()) {
            collectMethodDeclarations(iface, methodName, paramTypes, declarations, visited);
        }
    }

    private boolean matchesParamTypes(ConstrainedMethodMetadata methodMeta, Class<?>[] paramTypes) {
        return matchesParameterTypes(methodMeta, paramTypes);
    }

    private record MethodDeclarationInfo(Class<?> declaringClass, ConstrainedMethodMetadata methodMeta) {

        boolean hasParameterConstraints(int paramIndex) {
            if (methodMeta == null) {
                return false;
            }
            List<ConstrainedParameterMetadata> params = methodMeta.parameters();
            if (paramIndex >= params.size()) {
                return false;
            }
            if (!params.get(paramIndex).constraints().isEmpty()) {
                return true;
            }
            // Also check container element constraints
            for (ContainerElementConstraint cec : params.get(paramIndex).containerElementConstraints()) {
                if (!cec.constraints().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        boolean hasParameterCascading(int paramIndex) {
            if (methodMeta == null) {
                return false;
            }
            List<ConstrainedParameterMetadata> params = methodMeta.parameters();
            if (paramIndex >= params.size()) {
                return false;
            }
            if (params.get(paramIndex).cascading()) {
                return true;
            }
            // Also check container element cascading
            for (ContainerElementConstraint cec : params.get(paramIndex).containerElementConstraints()) {
                if (cec.cascading()) {
                    return true;
                }
            }
            return false;
        }

        Map<String, String> getParameterGroupConversions(int paramIndex) {
            if (methodMeta == null) {
                return Collections.emptyMap();
            }
            List<ConstrainedParameterMetadata> params = methodMeta.parameters();
            if (paramIndex >= params.size()) {
                return Collections.emptyMap();
            }
            return params.get(paramIndex).groupConversions();
        }

        Map<String, String> getReturnValueGroupConversions() {
            if (methodMeta == null) {
                return Collections.emptyMap();
            }
            return methodMeta.returnValueGroupConversions();
        }

        /**
         * Collects all container element group conversions on the return value,
         * keyed by type argument index, for parallel type conflict detection.
         */
        Map<Integer, Map<String, String>> getReturnValueContainerElementGroupConversions() {
            if (methodMeta == null) {
                return Collections.emptyMap();
            }
            Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
            for (ContainerElementConstraint cec : methodMeta.returnValueContainerElementConstraints()) {
                Map<String, String> gc = cec.groupConversions();
                if (!gc.isEmpty()) {
                    result.put(cec.typeArgumentIndex(), gc);
                }
            }
            return result;
        }

        boolean hasReturnValueContainerElementDuplicateFromGroups() {
            if (methodMeta == null) {
                return false;
            }
            Set<String> seenFrom = new HashSet<>();
            for (ContainerElementConstraint cec : methodMeta.returnValueContainerElementConstraints()) {
                for (String fromGroup : cec.groupConversions().keySet()) {
                    if (!seenFrom.add(fromGroup)) {
                        return true; // Duplicate "from" group
                    }
                }
            }
            return false;
        }
    }

    /**
     * Finds the most specific custom validator for the given constraint annotation and value type.
     * Checks all validators in @Constraint(validatedBy=...) and selects the one whose target type
     * is closest to the value type in the type hierarchy.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Class<? extends ConstraintValidator> findCustomValidator(String annotationClassName, Class<?> valueType) {
        // Get all validator class names from build-time metadata or reflection
        List<String> validatorClassNames = findAllValidatorClassNames(annotationClassName);
        if (validatorClassNames.isEmpty()) {
            return null;
        }

        Class<?> bestValidatorClass = null;
        Class<?> bestTargetClass = null;
        List<Class<?>> ambiguousValidators = null;

        for (String validatorClassName : validatorClassNames) {
            Class<?> validatorClass = loadClass(validatorClassName);

            boolean isCrossParam = isCrossParameterValidator(validatorClass);
            if (isCrossParam && !valueType.isArray()) {
                continue;
            }
            if (!isCrossParam && valueType.isArray() && valueType == Object[].class) {
                continue;
            }
            Class<?> targetClass = getValidatorTargetType(validatorClass);
            if (targetClass == null) {
                continue;
            }
            if (targetClass.isAssignableFrom(valueType)) {
                if (bestTargetClass == null) {
                    bestValidatorClass = validatorClass;
                    bestTargetClass = targetClass;
                } else if (bestTargetClass.equals(targetClass)) {
                    if (ambiguousValidators == null) {
                        ambiguousValidators = new ArrayList<>();
                        ambiguousValidators.add(bestValidatorClass);
                    }
                    ambiguousValidators.add(validatorClass);
                } else if (bestTargetClass.isAssignableFrom(targetClass)) {
                    bestValidatorClass = validatorClass;
                    bestTargetClass = targetClass;
                    ambiguousValidators = null;
                } else if (targetClass.isAssignableFrom(bestTargetClass)) {
                    // Current best is more specific, keep it
                } else {
                    if (ambiguousValidators == null) {
                        ambiguousValidators = new ArrayList<>();
                        ambiguousValidators.add(bestValidatorClass);
                    }
                    ambiguousValidators.add(validatorClass);
                }
            }
        }

        if (ambiguousValidators != null && ambiguousValidators.size() > 1) {
            throw new UnexpectedTypeException(
                    "More than one maximally specific validator found for type '"
                            + valueType.getName() + "' and constraint '"
                            + annotationClassName + "'");
        }

        return bestValidatorClass != null ? (Class<? extends ConstraintValidator>) bestValidatorClass : null;
    }

    private List<String> findAllValidatorClassNames(String annotationClassName) {
        List<String> mapped = metadata.constraintValidatorMapping().get(annotationClassName);
        return mapped != null ? mapped : Collections.emptyList();
    }

    /**
     * Checks if a validator class is a cross-parameter validator.
     * Uses build-time metadata discovered from @SupportedValidationTarget(ValidationTarget.PARAMETERS).
     * Falls back to checking target type for validators not in metadata.
     */
    private boolean isCrossParameterValidator(Class<?> validatorClass) {
        if (metadata.crossParameterValidatorClassNames().contains(validatorClass.getName())) {
            return true;
        }
        // Fallback: check if target type is Object[] (default cross-parameter without annotation)
        Class<?> targetType = getValidatorTargetType(validatorClass);
        return targetType != null && targetType.equals(Object[].class);
    }

    private Class<?> getValidatorTargetType(Class<?> validatorClass) {
        Class<?> cached = validatorTargetTypeCache.get(validatorClass.getName());
        if (cached != null) {
            return cached;
        }

        String targetTypeName = metadata.validatorTargetTypes().get(validatorClass.getName());
        if (targetTypeName != null) {
            Class<?> targetType = loadClass(targetTypeName);
            if (targetType != null) {
                validatorTargetTypeCache.put(validatorClass.getName(), targetType);
                return targetType;
            }
        }
        return null;
    }

    private static final String UNWRAP_PAYLOAD = "jakarta.validation.valueextraction.Unwrapping$Unwrap";

    private boolean hasUnwrapPayload(ConstraintMetadata constraint) {
        List<String> payload = constraint.payload();
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        for (String p : payload) {
            if (UNWRAP_PAYLOAD.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the Iterable type argument from a container class's generic hierarchy.
     * For example, for {@code SubClassHContainer extends ArrayList<SubClassH>},
     * returns {@code SubClassH.class}.
     * Uses build-time precomputed metadata when available.
     */
    private Class<?> resolveIterableTypeArgument(Class<?> containerClass) {
        String elementTypeName = metadata.iterableTypeArguments().get(containerClass.getName());
        if (elementTypeName != null) {
            return loadClass(elementTypeName);
        }
        return null;
    }

    private record MessageInterpolatorContext(ConstraintDescriptor<?> descriptor, Object validatedValue)
            implements
                MessageInterpolator.Context {

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return descriptor;
        }

        @Override
        public Object getValidatedValue() {
            return validatedValue;
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new ValidationException("Cannot unwrap to " + type);
        }
    }

    // --- Custom Value Extractor support ---

    private boolean isExtractorApplicable(ValueExtractor<?> extractor,
            Class<?> containerType) {
        // Walk the class hierarchy to find ValueExtractor's type parameter
        // (needed for Arc proxies where the proxy class itself doesn't have the generic interface)
        Class<?> current = extractor.getClass();
        while (current != null && current != Object.class) {
            for (Type iface : current.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType pt) {
                    if (ValueExtractor.class.equals(pt.getRawType())) {
                        Type extractedType = pt.getActualTypeArguments()[0];
                        Class<?> extractedRawType = getRawType(extractedType);
                        if (extractedRawType != null && extractedRawType.isAssignableFrom(containerType)) {
                            return true;
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private Class<?> getRawType(Type type) {
        if (type instanceof Class<?> cls) {
            return cls;
        } else if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return null;
    }

    private class CustomValueExtractorReceiver<T>
            implements jakarta.validation.valueextraction.ValueExtractor.ValueReceiver {

        private final Object rootBean;
        private final Class<?> rootBeanClass;
        private final Object leafBean;
        private final ContainerElementConstraint cec;
        private final PathImpl basePath;
        private final Set<Class<?>> groups;
        private final Set<ConstraintViolation<T>> violations;
        private final Object[] executableParameters;
        private final Object executableReturnValue;

        CustomValueExtractorReceiver(Object rootBean, Class<?> rootBeanClass, Object leafBean,
                ContainerElementConstraint cec, PathImpl basePath, Set<Class<?>> groups,
                Set<ConstraintViolation<T>> violations, Object[] executableParameters,
                Object executableReturnValue) {
            this.rootBean = rootBean;
            this.rootBeanClass = rootBeanClass;
            this.leafBean = leafBean;
            this.cec = cec;
            this.basePath = basePath;
            this.groups = groups;
            this.violations = violations;
            this.executableParameters = executableParameters;
            this.executableReturnValue = executableReturnValue;
        }

        @Override
        public void value(String nodeName, Object object) {
            for (ConstraintMetadata constraint : cec.constraints()) {
                if (matchesGroup(constraint, groups)) {
                    validateConstraint(rootBean, rootBeanClass, leafBean, object,
                            constraint, basePath, null, executableParameters,
                            executableReturnValue, violations, null);
                }
            }
        }

        @Override
        public void iterableValue(String nodeName, Object object) {
            value(nodeName, object);
        }

        @Override
        public void indexedValue(String nodeName, int i, Object object) {
            value(nodeName, object);
        }

        @Override
        public void keyedValue(String nodeName, Object key, Object object) {
            value(nodeName, object);
        }
    }

    private record GroupSequenceStep(Set<Class<?>> groups, boolean sequenced) {
    }

    private record ValidatorCacheKey(ConstraintMetadata constraint, Class<? extends ConstraintValidator<?, ?>> validatorClass) {
    }
}
