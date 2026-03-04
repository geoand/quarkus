package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ValidationException;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ConstructorDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import jakarta.validation.metadata.MethodType;
import jakarta.validation.metadata.ParameterDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;

import io.quarkus.bean.validation.impl.ValidationUtils;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedBeanMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedFieldMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedMethodMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedParameterMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstraintMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ContainerElementConstraint;

/**
 * Implementation of {@link BeanDescriptor} providing the metadata API for a validated bean class.
 * <p>
 * Use the {@link #build(Class, ConstrainedBeanMetadata, ConstraintValidatorFactory)} factory method
 * to construct instances from build-time metadata.
 */
public class BeanDescriptorImpl implements BeanDescriptor {

    private final Class<?> elementClass;
    private final boolean isBeanConstrained;
    private final Set<ConstraintDescriptor<?>> constraintDescriptors;
    private final Map<String, PropertyDescriptorImpl> propertyDescriptors;
    private final Map<String, MethodDescriptorImpl> methodDescriptors;
    private final Map<String, MethodDescriptorImpl> getterMethodDescriptors;
    private final Map<String, ConstructorDescriptorImpl> constructorDescriptors;

    private BeanDescriptorImpl(Class<?> elementClass,
            boolean isBeanConstrained,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            Map<String, PropertyDescriptorImpl> propertyDescriptors,
            Map<String, MethodDescriptorImpl> methodDescriptors,
            Map<String, MethodDescriptorImpl> getterMethodDescriptors,
            Map<String, ConstructorDescriptorImpl> constructorDescriptors) {
        this.elementClass = elementClass;
        this.isBeanConstrained = isBeanConstrained;
        this.constraintDescriptors = constraintDescriptors != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(constraintDescriptors))
                : Collections.emptySet();
        this.propertyDescriptors = propertyDescriptors != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(propertyDescriptors))
                : Collections.emptyMap();
        this.methodDescriptors = methodDescriptors != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(methodDescriptors))
                : Collections.emptyMap();
        this.getterMethodDescriptors = getterMethodDescriptors != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(getterMethodDescriptors))
                : Collections.emptyMap();
        this.constructorDescriptors = constructorDescriptors != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(constructorDescriptors))
                : Collections.emptyMap();
    }

    public static BeanDescriptorImpl build(Class<?> beanClass, ConstrainedBeanMetadata meta) {
        if (meta == null) {
            return build(beanClass, Collections.emptyList());
        }
        return build(beanClass, Collections.singletonList(meta));
    }

    /**
     * Builds a BeanDescriptor by merging metadata from the full type hierarchy.
     * Each entry in {@code allMeta} corresponds to a level in the hierarchy
     * (concrete class first, then superclasses, then interfaces).
     */
    public static BeanDescriptorImpl build(Class<?> beanClass, List<ConstrainedBeanMetadata> allMeta) {
        if (allMeta == null || allMeta.isEmpty()) {
            return new BeanDescriptorImpl(beanClass, false, Collections.emptySet(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap());
        }

        Set<ConstraintDescriptor<?>> classConstraints = new LinkedHashSet<>();
        Map<String, PropertyDescriptorImpl> propertyMap = new LinkedHashMap<>();
        Map<String, MethodDescriptorImpl> methodMap = new LinkedHashMap<>();
        Map<String, MethodDescriptorImpl> getterMethodMap = new LinkedHashMap<>();
        Map<String, ConstructorDescriptorImpl> ctorMap = new LinkedHashMap<>();

        for (ConstrainedBeanMetadata meta : allMeta) {
            // Class-level constraint descriptors
            classConstraints.addAll(buildConstraintDescriptors(meta.classConstraints()));

            // Property descriptors (from fields)
            for (ConstrainedFieldMetadata fieldMeta : meta.fields()) {
                mergeFieldIntoPropertyMap(propertyMap, fieldMeta);
            }

            // Method and getter-as-property descriptors
            for (ConstrainedMethodMetadata methodMeta : meta.methods()) {
                if (methodMeta.getter()) {
                    mergeGetterIntoPropertyMap(propertyMap, methodMeta);

                    // Also build a MethodDescriptor for getConstrainedMethods(GETTER)
                    MethodDescriptorImpl getterMethodDesc = buildGetterMethodDescriptor(
                            beanClass, methodMeta);
                    String getterKey = buildLookupKeyFromTypeNames(methodMeta.name(),
                            methodMeta.parameterTypeNames());
                    getterMethodMap.putIfAbsent(getterKey, getterMethodDesc);
                } else {
                    // Non-getter method
                    MethodDescriptorImpl methodDesc = buildMethodDescriptor(
                            beanClass, methodMeta);
                    String key = buildLookupKeyFromTypeNames(methodMeta.name(),
                            methodMeta.parameterTypeNames());
                    methodMap.putIfAbsent(key, methodDesc);
                }
            }

            // Constructor descriptors
            for (ConstrainedMethodMetadata ctorMeta : meta.constructors()) {
                ConstructorDescriptorImpl ctorDesc = buildConstructorDescriptor(
                        beanClass, ctorMeta);
                String key = buildLookupKeyFromTypeNames("<init>",
                        ctorMeta.parameterTypeNames());
                ctorMap.putIfAbsent(key, ctorDesc);
            }
        }

        boolean constrained = !classConstraints.isEmpty()
                || !propertyMap.isEmpty()
                || !methodMap.isEmpty()
                || !getterMethodMap.isEmpty()
                || !ctorMap.isEmpty();

        return new BeanDescriptorImpl(beanClass, constrained, classConstraints,
                propertyMap, methodMap, getterMethodMap, ctorMap);
    }

    private static void mergeFieldIntoPropertyMap(Map<String, PropertyDescriptorImpl> propertyMap,
            ConstrainedFieldMetadata fieldMeta) {
        Class<?> fieldType = loadClassOrDefault(fieldMeta.fieldTypeName());
        mergeProperty(propertyMap, fieldMeta.name(), fieldType,
                buildConstraintDescriptors(fieldMeta.constraints()),
                fieldMeta.cascading(),
                buildGroupConversions(fieldMeta.groupConversions()),
                buildContainerElementTypes(fieldMeta.containerElementConstraints()));
    }

    private static void mergeGetterIntoPropertyMap(Map<String, PropertyDescriptorImpl> propertyMap,
            ConstrainedMethodMetadata methodMeta) {
        String propertyName = getPropertyNameFromGetter(methodMeta.name());
        Class<?> returnType = loadClassOrDefault(methodMeta.returnTypeName());
        mergeProperty(propertyMap, propertyName, returnType,
                buildConstraintDescriptors(methodMeta.returnValueConstraints()),
                methodMeta.returnValueCascading(),
                buildGroupConversions(methodMeta.returnValueGroupConversions()),
                buildContainerElementTypes(methodMeta.returnValueContainerElementConstraints()));
    }

    private static void mergeProperty(Map<String, PropertyDescriptorImpl> propertyMap,
            String propertyName, Class<?> type,
            Set<ConstraintDescriptor<?>> constraints, boolean cascading,
            Set<GroupConversionDescriptor> groupConversions,
            Set<ContainerElementTypeDescriptor> containerElements) {
        PropertyDescriptorImpl existing = propertyMap.get(propertyName);
        if (existing != null) {
            Set<ConstraintDescriptor<?>> merged = new LinkedHashSet<>(existing.getConstraintDescriptors());
            merged.addAll(constraints);
            Set<GroupConversionDescriptor> mergedGc = new LinkedHashSet<>(existing.getGroupConversions());
            mergedGc.addAll(groupConversions);
            Set<ContainerElementTypeDescriptor> mergedCe = new LinkedHashSet<>(
                    existing.getConstrainedContainerElementTypes());
            mergedCe.addAll(containerElements);
            propertyMap.put(propertyName, new PropertyDescriptorImpl(
                    existing.getElementClass(), propertyName, merged,
                    existing.isCascaded() || cascading, mergedGc, mergedCe));
        } else {
            propertyMap.put(propertyName, new PropertyDescriptorImpl(
                    type, propertyName, constraints, cascading, groupConversions, containerElements));
        }
    }

    // --- BeanDescriptor methods ---

    @Override
    public boolean isBeanConstrained() {
        return isBeanConstrained;
    }

    @Override
    public PropertyDescriptor getConstraintsForProperty(String propertyName) {
        return propertyDescriptors.get(propertyName);
    }

    @Override
    public Set<PropertyDescriptor> getConstrainedProperties() {
        return new LinkedHashSet<>(propertyDescriptors.values());
    }

    @Override
    public MethodDescriptor getConstraintsForMethod(String methodName, Class<?>... parameterTypes) {
        String key = buildLookupKey(methodName, parameterTypes);
        MethodDescriptor result = methodDescriptors.get(key);
        if (result == null) {
            result = getterMethodDescriptors.get(key);
        }
        return result;
    }

    @Override
    public Set<MethodDescriptor> getConstrainedMethods(MethodType methodType, MethodType... methodTypes) {
        Set<MethodType> typeSet = EnumSet.noneOf(MethodType.class);
        typeSet.add(methodType);
        if (methodTypes != null) {
            typeSet.addAll(Arrays.asList(methodTypes));
        }

        Set<MethodDescriptor> result = new LinkedHashSet<>();
        if (typeSet.contains(MethodType.NON_GETTER)) {
            result.addAll(methodDescriptors.values());
        }
        if (typeSet.contains(MethodType.GETTER)) {
            result.addAll(getterMethodDescriptors.values());
        }

        return result;
    }

    @Override
    public ConstructorDescriptor getConstraintsForConstructor(Class<?>... parameterTypes) {
        String key = buildLookupKey("<init>", parameterTypes);
        return constructorDescriptors.get(key);
    }

    @Override
    public Set<ConstructorDescriptor> getConstrainedConstructors() {
        return new LinkedHashSet<>(constructorDescriptors.values());
    }

    // --- ElementDescriptor methods ---

    @Override
    public boolean hasConstraints() {
        return !constraintDescriptors.isEmpty();
    }

    @Override
    public Class<?> getElementClass() {
        return elementClass;
    }

    @Override
    public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
        return constraintDescriptors;
    }

    @Override
    public ConstraintFinder findConstraints() {
        return new ConstraintFinderImpl(constraintDescriptors);
    }

    public <T> T unwrap(Class<T> type) {
        throw new ValidationException("Cannot unwrap to " + type);
    }

    // --- Internal builder methods ---

    @SuppressWarnings("unchecked")
    static Set<ConstraintDescriptor<?>> buildConstraintDescriptors(
            List<ConstraintMetadata> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ConstraintDescriptor<?>> result = new LinkedHashSet<>();
        for (ConstraintMetadata cm : constraints) {
            try {
                Class<? extends Annotation> annotationType = (Class<? extends Annotation>) Class
                        .forName(cm.annotationClassName(), false,
                                Thread.currentThread().getContextClassLoader());
                result.add(new ConstraintDescriptorImpl<>(
                        annotationType,
                        cm.attributes(),
                        cm.groups(),
                        cm.payload(),
                        cm.validatorClassName(),
                        cm.reportAsSingleViolation(),
                        cm.getResolvedComposingConstraints()));
            } catch (ClassNotFoundException e) {
                throw new ValidationException(
                        "Cannot load constraint annotation class: " + cm.annotationClassName(), e);
            }
        }
        return result;
    }

    static Set<GroupConversionDescriptor> buildGroupConversions(Map<String, String> conversions) {
        if (conversions == null || conversions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<GroupConversionDescriptor> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : conversions.entrySet()) {
            try {
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                Class<?> from = Class.forName(entry.getKey(), false, tccl);
                Class<?> to = Class.forName(entry.getValue(), false, tccl);
                result.add(new GroupConversionDescriptorImpl(from, to));
            } catch (ClassNotFoundException e) {
                throw new ValidationException("Cannot load group conversion class", e);
            }
        }
        return result;
    }

    static Set<ContainerElementTypeDescriptor> buildContainerElementTypes(
            List<ContainerElementConstraint> containerElements) {
        if (containerElements == null || containerElements.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ContainerElementTypeDescriptor> result = new LinkedHashSet<>();
        for (ContainerElementConstraint cec : containerElements) {
            Set<ConstraintDescriptor<?>> cecConstraints = buildConstraintDescriptors(
                    cec.constraints());
            Class<?> containerClass;
            containerClass = loadClassOrDefault(cec.containerClassName());
            result.add(new ContainerElementTypeDescriptorImpl(
                    containerClass,
                    cec.typeArgumentIndex(),
                    cecConstraints,
                    cec.cascading(),
                    Collections.emptySet()));
        }
        return result;
    }

    private static MethodDescriptorImpl buildMethodDescriptor(Class<?> beanClass,
            ConstrainedMethodMetadata methodMeta) {
        List<ParameterDescriptor> paramDescs = buildParameterDescriptorsFromMeta(
                methodMeta.parameters());
        Class<?> returnType = loadClassOrDefault(methodMeta.returnTypeName());
        ReturnValueDescriptorImpl returnDesc = buildReturnValueDescriptor(returnType, methodMeta);
        Set<ConstraintDescriptor<?>> crossParamConstraints = buildConstraintDescriptors(
                methodMeta.crossParameterConstraints());
        return new MethodDescriptorImpl(methodMeta.name(), beanClass,
                combineConstraints(returnDesc, crossParamConstraints),
                paramDescs, returnDesc, new CrossParameterDescriptorImpl(crossParamConstraints));
    }

    private static MethodDescriptorImpl buildGetterMethodDescriptor(Class<?> beanClass,
            ConstrainedMethodMetadata methodMeta) {
        Class<?> returnType = loadClassOrDefault(methodMeta.returnTypeName());
        ReturnValueDescriptorImpl returnDesc = buildReturnValueDescriptor(returnType, methodMeta);
        return new MethodDescriptorImpl(methodMeta.name(), beanClass,
                returnDesc.getConstraintDescriptors(),
                Collections.emptyList(), returnDesc,
                new CrossParameterDescriptorImpl(Collections.emptySet()));
    }

    private static ConstructorDescriptorImpl buildConstructorDescriptor(Class<?> beanClass,
            ConstrainedMethodMetadata ctorMeta) {
        List<ParameterDescriptor> paramDescs = buildParameterDescriptorsFromMeta(
                ctorMeta.parameters());
        ReturnValueDescriptorImpl returnDesc = buildReturnValueDescriptor(beanClass, ctorMeta);
        Set<ConstraintDescriptor<?>> crossParamConstraints = buildConstraintDescriptors(
                ctorMeta.crossParameterConstraints());
        return new ConstructorDescriptorImpl(beanClass.getSimpleName(), beanClass,
                combineConstraints(returnDesc, crossParamConstraints),
                paramDescs, returnDesc, new CrossParameterDescriptorImpl(crossParamConstraints));
    }

    private static Set<ConstraintDescriptor<?>> combineConstraints(
            ReturnValueDescriptorImpl returnDesc,
            Set<ConstraintDescriptor<?>> crossParamConstraints) {
        Set<ConstraintDescriptor<?>> all = new LinkedHashSet<>(returnDesc.getConstraintDescriptors());
        all.addAll(crossParamConstraints);
        return all;
    }

    // --- Key-building and return value helpers ---

    private static String buildLookupKey(String name, Class<?>... parameterTypes) {
        StringBuilder sb = new StringBuilder(name);
        sb.append('(');
        if (parameterTypes != null) {
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(parameterTypes[i].getName());
            }
        }
        sb.append(')');
        return sb.toString();
    }

    private static String buildLookupKeyFromTypeNames(String name, List<String> parameterTypeNames) {
        StringBuilder sb = new StringBuilder(name);
        sb.append('(');
        if (parameterTypeNames != null) {
            for (int i = 0; i < parameterTypeNames.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(parameterTypeNames.get(i));
            }
        }
        sb.append(')');
        return sb.toString();
    }

    private static List<ParameterDescriptor> buildParameterDescriptorsFromMeta(
            List<ConstrainedParameterMetadata> paramMetas) {
        if (paramMetas == null || paramMetas.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParameterDescriptor> result = new ArrayList<>();
        for (ConstrainedParameterMetadata paramMeta : paramMetas) {
            Set<ConstraintDescriptor<?>> paramConstraints = buildConstraintDescriptors(
                    paramMeta.constraints());
            Set<GroupConversionDescriptor> paramGc = buildGroupConversions(
                    paramMeta.groupConversions());
            Set<ContainerElementTypeDescriptor> paramCe = buildContainerElementTypes(
                    paramMeta.containerElementConstraints());

            Class<?> paramType = loadClassOrDefault(paramMeta.typeName());

            result.add(new ParameterDescriptorImpl(
                    paramMeta.name(),
                    paramMeta.index(),
                    paramType,
                    paramConstraints,
                    paramMeta.cascading(),
                    paramGc,
                    paramCe));
        }
        return result;
    }

    private static ReturnValueDescriptorImpl buildReturnValueDescriptor(
            Class<?> returnType, ConstrainedMethodMetadata meta) {
        Set<ConstraintDescriptor<?>> constraints = buildConstraintDescriptors(
                meta.returnValueConstraints());
        Set<GroupConversionDescriptor> groupConversions = buildGroupConversions(
                meta.returnValueGroupConversions());
        Set<ContainerElementTypeDescriptor> containerElements = buildContainerElementTypes(
                meta.returnValueContainerElementConstraints());
        return new ReturnValueDescriptorImpl(
                returnType, constraints, meta.returnValueCascading(),
                groupConversions, containerElements);
    }

    private static Class<?> loadClassOrDefault(String className) {
        if (className == null) {
            return Object.class;
        }
        try {
            Class<?> primitive = ValidationUtils.PRIMITIVE_TYPES.get(className);
            if (primitive != null) {
                return primitive;
            }
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            return Object.class;
        }
    }

    private static String getPropertyNameFromGetter(String methodName) {
        return ValidationUtils.getPropertyNameFromGetter(methodName);
    }
}
