package io.quarkus.jackson.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.BasicClassIntrospector;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.introspect.PotentialCreator;
import com.fasterxml.jackson.databind.introspect.PotentialCreators;
import com.fasterxml.jackson.databind.introspect.PrecomputedAnnotatedClass;
import com.fasterxml.jackson.databind.introspect.PrecomputedBeanDescription;
import com.fasterxml.jackson.databind.introspect.PrecomputedPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.TypeResolutionContext;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;

/**
 * A {@link ClassIntrospector} that returns precomputed metadata for known types,
 * bypassing Jackson's reflection-based class introspection (hierarchy walking,
 * property correlation via {@code POJOPropertiesCollector}).
 * <p>
 * For types not known at build time, delegates to the standard {@link BasicClassIntrospector}.
 * <p>
 * Supports public-field POJOs, JavaBean-style classes, {@code @JsonCreator} constructors,
 * {@code @JsonValue} methods, and {@code @JsonAnyGetter}/{@code @JsonAnySetter} methods.
 * The reflect objects and their annotations are resolved once at startup from metadata
 * determined at build time via Jandex.
 */
public class PrecomputedClassIntrospector extends ClassIntrospector {

    private static final Logger LOG = Logger.getLogger(PrecomputedClassIntrospector.class);

    private final BasicClassIntrospector fallback;
    private final Map<String, PrecomputedClassMetadata> classToMetadata;
    private final Map<Class<?>, ResolvedClassInfo> resolvedClasses;

    /**
     * @param creatorConstructor @JsonCreator constructor (may be null)
     * @param jsonValueMethod @JsonValue method or field (may be null)
     * @param anyGetterMethod @JsonAnyGetter method (may be null)
     * @param creatorFactoryMethod @JsonCreator static factory method (may be null)
     * @param anySetterMethod @JsonAnySetter method (may be null)
     */
    record ResolvedClassInfo(Class<?> clazz,
            Constructor<?> defaultConstructor,
            AnnotationMap classAnnotations,
            AnnotationMap defaultCtorAnnotations,
            List<ResolvedProperty> properties,
            Set<String> ignoredProperties,
            Constructor<?> creatorConstructor,
            AnnotationMap creatorCtorAnnotations,
            AnnotationMap[] creatorCtorParamAnnotations,
            Method creatorFactoryMethod,
            AnnotationMap creatorFactoryMethodAnnotations,
            AnnotationMap[] creatorFactoryMethodParamAnnotations,
            Method jsonValueMethod,
            AnnotationMap jsonValueAnnotations,
            Field jsonValueField,
            AnnotationMap jsonValueFieldAnnotations,
            Method anyGetterMethod,
            AnnotationMap anyGetterAnnotations,
            Method anySetterMethod,
            AnnotationMap anySetterAnnotations,
            AnnotationMap[] anySetterParamAnnotations) {
    }

    record ResolvedProperty(String propertyName,
            Field field,
            Method getter,
            Method setter,
            AnnotationMap fieldAnnotations,
            AnnotationMap getterAnnotations,
            AnnotationMap setterAnnotations,
            AnnotationMap[] setterParamAnnotations) {
    }

    public PrecomputedClassIntrospector(Map<String, PrecomputedClassMetadata> classToMetadata) {
        this.fallback = new BasicClassIntrospector();
        this.classToMetadata = classToMetadata;
        this.resolvedClasses = new ConcurrentHashMap<>();
    }

    private PrecomputedClassIntrospector(BasicClassIntrospector fallback,
            Map<String, PrecomputedClassMetadata> classToMetadata,
            Map<Class<?>, ResolvedClassInfo> resolvedClasses) {
        this.fallback = fallback;
        this.classToMetadata = classToMetadata;
        this.resolvedClasses = resolvedClasses;
    }

    private ResolvedClassInfo getResolvedInfo(Class<?> clazz) {
        ResolvedClassInfo cached = resolvedClasses.get(clazz);
        if (cached != null) {
            return cached;
        }
        PrecomputedClassMetadata metadata = classToMetadata.get(clazz.getName());
        if (metadata == null) {
            return null;
        }
        ResolvedClassInfo resolved = resolveMetadata(clazz, metadata);
        if (resolved != null) {
            resolvedClasses.putIfAbsent(clazz, resolved);
            return resolvedClasses.get(clazz);
        }
        return null;
    }

    private static ResolvedClassInfo resolveMetadata(Class<?> clazz, PrecomputedClassMetadata metadata) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Constructor<?> defaultCtor = null;
            try {
                defaultCtor = clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                // no default constructor
            }

            // Pre-compute class-level and constructor annotations once
            AnnotationMap classAnns = collectClassAnnotations(clazz);
            AnnotationMap ctorAnns = defaultCtor != null ? collectAnnotations(defaultCtor) : null;

            // Resolve properties
            List<ResolvedProperty> properties = new ArrayList<>();
            for (PrecomputedPropertyInfo propInfo : metadata.properties()) {
                Field field = null;
                AnnotationMap fieldAnns = null;
                if (propInfo.getFieldName() != null) {
                    Class<?> declaringClass = propInfo.getDeclaringClassName() != null
                            ? cl.loadClass(propInfo.getDeclaringClassName())
                            : clazz;
                    field = declaringClass.getDeclaredField(propInfo.getFieldName());
                    fieldAnns = collectAnnotations(field);
                }
                Method getter = null;
                AnnotationMap getterAnns = null;
                if (propInfo.getGetterName() != null) {
                    getter = clazz.getMethod(propInfo.getGetterName());
                    getterAnns = collectAnnotations(getter);
                }
                Method setter = null;
                AnnotationMap setterAnns = null;
                AnnotationMap[] setterParamAnns = null;
                if (propInfo.getSetterName() != null && propInfo.getSetterParamType() != null) {
                    Class<?> paramType = loadClass(cl, propInfo.getSetterParamType());
                    setter = clazz.getMethod(propInfo.getSetterName(), paramType);
                    setterAnns = collectAnnotations(setter);
                    setterParamAnns = collectMethodParameterAnnotations(setter);
                }
                properties.add(new ResolvedProperty(propInfo.getPropertyName(), field, getter, setter,
                        fieldAnns, getterAnns, setterAnns, setterParamAnns));
            }

            // Resolve @JsonIgnoreProperties
            Set<String> ignoredProperties = null;
            if (metadata.ignoredProperties() != null && !metadata.ignoredProperties().isEmpty()) {
                ignoredProperties = new HashSet<>(metadata.ignoredProperties());
            }

            // Resolve @JsonCreator constructor
            Constructor<?> creatorCtor = null;
            AnnotationMap creatorCtorAnns = null;
            AnnotationMap[] creatorCtorParamAnns = null;
            if (metadata.creatorConstructorParamTypes() != null) {
                Class<?>[] paramTypes = new Class<?>[metadata.creatorConstructorParamTypes().size()];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = loadClass(cl, metadata.creatorConstructorParamTypes().get(i));
                }
                creatorCtor = clazz.getDeclaredConstructor(paramTypes);
                creatorCtorAnns = collectAnnotations(creatorCtor);
                creatorCtorParamAnns = collectConstructorParameterAnnotations(creatorCtor);
                injectParamNames(creatorCtorParamAnns, metadata.creatorConstructorParamNames());
            }

            // Resolve @JsonCreator static factory method
            Method creatorFactoryMethod = null;
            AnnotationMap creatorFactoryAnns = null;
            AnnotationMap[] creatorFactoryParamAnns = null;
            if (metadata.creatorFactoryMethodName() != null) {
                Class<?> declaringClass = metadata.creatorFactoryMethodDeclaringClass() != null
                        ? cl.loadClass(metadata.creatorFactoryMethodDeclaringClass())
                        : clazz;
                Class<?>[] paramTypes = new Class<?>[metadata.creatorFactoryMethodParamTypes().size()];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = loadClass(cl, metadata.creatorFactoryMethodParamTypes().get(i));
                }
                creatorFactoryMethod = declaringClass.getDeclaredMethod(
                        metadata.creatorFactoryMethodName(), paramTypes);
                creatorFactoryAnns = collectAnnotations(creatorFactoryMethod);
                creatorFactoryParamAnns = collectMethodParameterAnnotations(creatorFactoryMethod);
                injectParamNames(creatorFactoryParamAnns, metadata.creatorFactoryMethodParamNames());
            }

            // Resolve @JsonValue method or field
            Method jsonValueMethod = null;
            AnnotationMap jsonValueAnns = null;
            if (metadata.jsonValueMethodName() != null) {
                jsonValueMethod = clazz.getMethod(metadata.jsonValueMethodName());
                jsonValueAnns = collectAnnotations(jsonValueMethod);
            }
            Field jsonValueField = null;
            AnnotationMap jsonValueFieldAnns = null;
            if (metadata.jsonValueFieldName() != null) {
                Class<?> declaringClass = metadata.jsonValueFieldDeclaringClass() != null
                        ? cl.loadClass(metadata.jsonValueFieldDeclaringClass())
                        : clazz;
                jsonValueField = declaringClass.getDeclaredField(metadata.jsonValueFieldName());
                jsonValueFieldAnns = collectAnnotations(jsonValueField);
            }

            // Resolve @JsonAnyGetter method
            Method anyGetterMethod = null;
            AnnotationMap anyGetterAnns = null;
            if (metadata.anyGetterMethodName() != null) {
                anyGetterMethod = clazz.getMethod(metadata.anyGetterMethodName());
                anyGetterAnns = collectAnnotations(anyGetterMethod);
            }

            // Resolve @JsonAnySetter method
            Method anySetterMethod = null;
            AnnotationMap anySetterAnns = null;
            AnnotationMap[] anySetterParamAnns = null;
            if (metadata.anySetterMethodName() != null && metadata.anySetterParamType() != null) {
                Class<?> paramType = loadClass(cl, metadata.anySetterParamType());
                anySetterMethod = clazz.getMethod(metadata.anySetterMethodName(), String.class, paramType);
                anySetterAnns = collectAnnotations(anySetterMethod);
                anySetterParamAnns = collectMethodParameterAnnotations(anySetterMethod);
            }

            LOG.debugf("Precomputed Jackson metadata for %s (%d properties)", clazz.getName(), properties.size());
            return new ResolvedClassInfo(clazz, defaultCtor, classAnns, ctorAnns,
                    properties, ignoredProperties,
                    creatorCtor, creatorCtorAnns, creatorCtorParamAnns,
                    creatorFactoryMethod, creatorFactoryAnns, creatorFactoryParamAnns,
                    jsonValueMethod, jsonValueAnns,
                    jsonValueField, jsonValueFieldAnns,
                    anyGetterMethod, anyGetterAnns,
                    anySetterMethod, anySetterAnns, anySetterParamAnns);
        } catch (Exception e) {
            LOG.warnf("Failed to resolve precomputed Jackson metadata for %s: %s",
                    clazz.getName(), e.getMessage());
            return null;
        }
    }

    @Override
    public ClassIntrospector copy() {
        return new PrecomputedClassIntrospector(
                (BasicClassIntrospector) fallback.copy(),
                classToMetadata,
                resolvedClasses);
    }

    @Override
    public BeanDescription forSerialization(SerializationConfig cfg, JavaType type, ClassIntrospector.MixInResolver r) {
        ResolvedClassInfo info = getResolvedInfo(type.getRawClass());
        if (info != null && !hasMixIn(r, type.getRawClass())) {
            return buildBeanDescription(cfg, type, info);
        }
        return fallback.forSerialization(cfg, type, r);
    }

    @Override
    public BeanDescription forDeserialization(DeserializationConfig cfg, JavaType type, ClassIntrospector.MixInResolver r) {
        ResolvedClassInfo info = getResolvedInfo(type.getRawClass());
        if (info != null && !hasMixIn(r, type.getRawClass())) {
            return buildBeanDescription(cfg, type, info);
        }
        return fallback.forDeserialization(cfg, type, r);
    }

    @Override
    public BeanDescription forDeserializationWithBuilder(DeserializationConfig cfg,
            JavaType builderType, ClassIntrospector.MixInResolver r, BeanDescription valueTypeDesc) {
        return fallback.forDeserializationWithBuilder(cfg, builderType, r, valueTypeDesc);
    }

    @Override
    public BeanDescription forCreation(DeserializationConfig cfg, JavaType type, ClassIntrospector.MixInResolver r) {
        ResolvedClassInfo info = getResolvedInfo(type.getRawClass());
        if (info != null && !hasMixIn(r, type.getRawClass())) {
            return buildBeanDescription(cfg, type, info);
        }
        return fallback.forCreation(cfg, type, r);
    }

    @Override
    public BeanDescription forClassAnnotations(MapperConfig<?> cfg, JavaType type, ClassIntrospector.MixInResolver r) {
        ResolvedClassInfo info = getResolvedInfo(type.getRawClass());
        if (info != null && !hasMixIn(r, type.getRawClass())) {
            PrecomputedAnnotatedClass ac = buildAnnotatedClass(cfg, type, info);
            return BasicBeanDescription.forOtherUse(cfg, type, ac);
        }
        return fallback.forClassAnnotations(cfg, type, r);
    }

    @Override
    public BeanDescription forDirectClassAnnotations(MapperConfig<?> cfg, JavaType type, ClassIntrospector.MixInResolver r) {
        ResolvedClassInfo info = getResolvedInfo(type.getRawClass());
        if (info != null && !hasMixIn(r, type.getRawClass())) {
            PrecomputedAnnotatedClass ac = buildAnnotatedClass(cfg, type, info);
            return BasicBeanDescription.forOtherUse(cfg, type, ac);
        }
        return fallback.forDirectClassAnnotations(cfg, type, r);
    }

    private static boolean hasMixIn(ClassIntrospector.MixInResolver r, Class<?> clazz) {
        return r != null && r.findMixInClassFor(clazz) != null;
    }

    private PrecomputedAnnotatedClass buildAnnotatedClass(
            MapperConfig<?> config, JavaType type, ResolvedClassInfo info) {
        TypeResolutionContext trc = new TypeResolutionContext.Basic(
                config.getTypeFactory(), type.getBindings());

        List<AnnotatedField> annotatedFields = new ArrayList<>();
        List<AnnotatedMethod> annotatedMethods = new ArrayList<>();

        for (ResolvedProperty prop : info.properties) {
            if (prop.field != null) {
                annotatedFields.add(new AnnotatedField(trc, prop.field, prop.fieldAnnotations));
            }
            if (prop.getter != null) {
                annotatedMethods.add(new AnnotatedMethod(trc, prop.getter, prop.getterAnnotations, null));
            }
            if (prop.setter != null) {
                annotatedMethods.add(new AnnotatedMethod(trc, prop.setter, prop.setterAnnotations,
                        prop.setterParamAnnotations));
            }
        }

        // Include @JsonValue field or method
        if (info.jsonValueField != null) {
            annotatedFields.add(new AnnotatedField(trc, info.jsonValueField, info.jsonValueFieldAnnotations));
        }
        if (info.jsonValueMethod != null) {
            annotatedMethods.add(new AnnotatedMethod(trc, info.jsonValueMethod, info.jsonValueAnnotations, null));
        }
        if (info.anyGetterMethod != null) {
            annotatedMethods.add(new AnnotatedMethod(trc, info.anyGetterMethod, info.anyGetterAnnotations, null));
        }
        if (info.anySetterMethod != null) {
            annotatedMethods.add(new AnnotatedMethod(trc, info.anySetterMethod, info.anySetterAnnotations,
                    info.anySetterParamAnnotations));
        }

        // Default constructor
        AnnotatedConstructor defaultCtor = null;
        if (info.defaultConstructor != null) {
            defaultCtor = new AnnotatedConstructor(trc, info.defaultConstructor,
                    info.defaultCtorAnnotations, null);
        }

        // @JsonCreator constructor
        List<AnnotatedConstructor> constructors = Collections.emptyList();
        if (info.creatorConstructor != null) {
            AnnotatedConstructor creatorCtor = new AnnotatedConstructor(trc, info.creatorConstructor,
                    info.creatorCtorAnnotations, info.creatorCtorParamAnnotations);
            constructors = List.of(creatorCtor);
        }

        // @JsonCreator static factory methods
        List<AnnotatedMethod> factoryMethods = Collections.emptyList();
        if (info.creatorFactoryMethod != null) {
            AnnotatedMethod factoryMethod = new AnnotatedMethod(trc, info.creatorFactoryMethod,
                    info.creatorFactoryMethodAnnotations, info.creatorFactoryMethodParamAnnotations);
            factoryMethods = List.of(factoryMethod);
            annotatedMethods.add(factoryMethod);
        }

        return new PrecomputedAnnotatedClass(
                type, info.clazz,
                type.getBindings(),
                info.classAnnotations,
                annotatedFields,
                annotatedMethods,
                new AnnotatedClass.Creators(defaultCtor, constructors, factoryMethods));
    }

    private PrecomputedBeanDescription buildBeanDescription(
            MapperConfig<?> config, JavaType type, ResolvedClassInfo info) {
        PrecomputedAnnotatedClass ac = buildAnnotatedClass(config, type, info);
        TypeResolutionContext trc = new TypeResolutionContext.Basic(
                config.getTypeFactory(), type.getBindings());

        PropertyNamingStrategy naming = config.getPropertyNamingStrategy();
        // Check for @JsonNaming on the class — overrides global naming strategy,
        // matching POJOPropertiesCollector._findNamingStrategy()
        JsonNaming jsonNaming = info.classAnnotations.get(JsonNaming.class);
        if (jsonNaming != null) {
            try {
                naming = jsonNaming.value().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                LOG.warnf("Failed to instantiate @JsonNaming strategy %s: %s",
                        jsonNaming.value().getName(), e.getMessage());
            }
        }
        List<BeanPropertyDefinition> props = new ArrayList<>();

        for (ResolvedProperty resolvedProp : info.properties) {
            // Merge annotations across members, matching POJOPropertyBuilder.mergeAnnotations():
            // for serialization, field annotations are merged into getter;
            // for deserialization, field/getter annotations are merged into setter.
            // Since PrecomputedPropertyDefinition serves both, we merge into all members.
            AnnotationMap mergedGetterAnns = mergeAnnotationMaps(
                    resolvedProp.getterAnnotations, resolvedProp.fieldAnnotations);
            AnnotationMap mergedSetterAnns = mergeAnnotationMaps(
                    resolvedProp.setterAnnotations, resolvedProp.fieldAnnotations, resolvedProp.getterAnnotations);
            AnnotationMap mergedFieldAnns = mergeAnnotationMaps(
                    resolvedProp.fieldAnnotations, resolvedProp.getterAnnotations);

            AnnotatedField af = resolvedProp.field != null
                    ? new AnnotatedField(trc, resolvedProp.field, mergedFieldAnns)
                    : null;
            AnnotatedMethod ag = resolvedProp.getter != null
                    ? new AnnotatedMethod(trc, resolvedProp.getter, mergedGetterAnns, null)
                    : null;
            AnnotatedMethod as = resolvedProp.setter != null
                    ? new AnnotatedMethod(trc, resolvedProp.setter, mergedSetterAnns,
                            resolvedProp.setterParamAnnotations)
                    : null;

            String propName = resolvedProp.propertyName;
            if (naming != null) {
                if (ag != null) {
                    propName = naming.nameForGetterMethod(config, ag, propName);
                } else if (af != null) {
                    propName = naming.nameForField(config, af, propName);
                }
            }

            props.add(new PrecomputedPropertyDefinition(
                    PropertyName.construct(propName), af, ag, as));
        }

        // Build @JsonCreator constructor list and PotentialCreators
        PotentialCreators potentialCreators = new PotentialCreators();
        List<AnnotatedConstructor> constructors = Collections.emptyList();
        if (info.creatorConstructor != null) {
            AnnotatedConstructor creatorCtor = new AnnotatedConstructor(trc, info.creatorConstructor,
                    info.creatorCtorAnnotations, info.creatorCtorParamAnnotations);
            constructors = List.of(creatorCtor);
            // Populate PotentialCreators so Jackson's deserialization can find the creator
            JsonCreator.Mode mode = config.getAnnotationIntrospector()
                    .findCreatorAnnotation(config, creatorCtor);
            PotentialCreator pc = new PotentialCreator(creatorCtor, mode);
            potentialCreators.setPropertiesBased(config, pc, "precomputed");
            assignCreatorPropertyDefs(config, pc, naming);
        }

        // Build @JsonCreator factory method list
        List<AnnotatedMethod> factoryMethods = Collections.emptyList();
        if (info.creatorFactoryMethod != null) {
            AnnotatedMethod factoryMethod = new AnnotatedMethod(trc, info.creatorFactoryMethod,
                    info.creatorFactoryMethodAnnotations, info.creatorFactoryMethodParamAnnotations);
            factoryMethods = List.of(factoryMethod);
            if (!potentialCreators.hasPropertiesBased()) {
                JsonCreator.Mode mode = config.getAnnotationIntrospector()
                        .findCreatorAnnotation(config, factoryMethod);
                PotentialCreator pc = new PotentialCreator(factoryMethod, mode);
                potentialCreators.setPropertiesBased(config, pc, "precomputed");
                assignCreatorPropertyDefs(config, pc, naming);
            }
        }

        // Build @JsonValue accessor (field or method)
        AnnotatedMember jsonValueAccessor = null;
        if (info.jsonValueField != null) {
            jsonValueAccessor = new AnnotatedField(trc, info.jsonValueField, info.jsonValueFieldAnnotations);
        } else if (info.jsonValueMethod != null) {
            jsonValueAccessor = new AnnotatedMethod(trc, info.jsonValueMethod, info.jsonValueAnnotations, null);
        }

        // Build @JsonAnyGetter accessor
        AnnotatedMethod anyGetter = null;
        if (info.anyGetterMethod != null) {
            anyGetter = new AnnotatedMethod(trc, info.anyGetterMethod, info.anyGetterAnnotations, null);
        }

        // Build @JsonAnySetter accessor
        AnnotatedMethod anySetter = null;
        if (info.anySetterMethod != null) {
            anySetter = new AnnotatedMethod(trc, info.anySetterMethod, info.anySetterAnnotations,
                    info.anySetterParamAnnotations);
        }

        // Build ignored properties set
        Set<String> ignoredPropertyNames = info.ignoredProperties != null
                ? info.ignoredProperties
                : Collections.emptySet();

        return new PrecomputedBeanDescription(
                config, type, ac,
                props,
                potentialCreators,
                constructors,
                factoryMethods,
                ac.getDefaultConstructor(),
                anyGetter, anySetter, jsonValueAccessor, null,
                ignoredPropertyNames,
                Collections.emptyMap(),
                null, null, null, null);
    }

    /**
     * Merges multiple annotation maps into one, with earlier maps taking precedence.
     * This replicates the behavior of {@code POJOPropertyBuilder.mergeAnnotations()} where
     * annotations from field, getter, and setter are merged so that any member can see
     * annotations declared on any other member of the same property (e.g., {@code @SecureField}
     * on a field is visible through the getter's annotation map).
     */
    private static AnnotationMap mergeAnnotationMaps(AnnotationMap primary, AnnotationMap... others) {
        if (primary == null && others.length == 0) {
            return new AnnotationMap();
        }
        AnnotationMap merged = new AnnotationMap();
        // Add from others first (lower priority)
        for (int i = others.length - 1; i >= 0; i--) {
            if (others[i] != null) {
                for (Annotation ann : others[i].annotations()) {
                    merged.add(ann);
                }
            }
        }
        // Add primary last (highest priority — overwrites)
        if (primary != null) {
            for (Annotation ann : primary.annotations()) {
                merged.add(ann);
            }
        }
        return merged;
    }

    /**
     * Creates and assigns {@link BeanPropertyDefinition}s for each parameter of a
     * properties-based creator. These property definitions are needed by Jackson's
     * {@code CreatorCandidate.construct()} during deserialization.
     * <p>
     * When a naming strategy is active, implicit parameter names (those without
     * {@code @JsonProperty}) are renamed to match the serialized property names,
     * so that deserialization can map incoming JSON keys to creator parameters.
     */
    private static void assignCreatorPropertyDefs(MapperConfig<?> config, PotentialCreator pc,
            PropertyNamingStrategy naming) {
        int paramCount = pc.paramCount();
        List<BeanPropertyDefinition> creatorPropDefs = new ArrayList<>(paramCount);
        for (int i = 0; i < paramCount; i++) {
            AnnotatedParameter param = pc.param(i);
            PropertyName explicitName = pc.explicitName(i);
            PropertyName name = explicitName;
            if (name == null) {
                name = pc.implicitName(i);
            }
            if (name == null) {
                name = PropertyName.construct("arg" + i);
            }
            // Apply naming strategy when there's no user-provided @JsonProperty name.
            // If the explicit name matches the implicit name, it was injected by
            // injectParamNames() and should be treated as implicit for naming purposes.
            if (naming != null) {
                boolean applyNaming;
                if (explicitName == null) {
                    applyNaming = true;
                } else {
                    PropertyName implName = pc.implicitName(i);
                    applyNaming = implName != null
                            && explicitName.getSimpleName().equals(implName.getSimpleName());
                }
                if (applyNaming) {
                    String renamed = naming.nameForConstructorParameter(config, param, name.getSimpleName());
                    if (renamed != null) {
                        name = PropertyName.construct(renamed);
                    }
                }
            }
            creatorPropDefs.add(SimpleBeanPropertyDefinition.construct(config, param, name));
        }
        pc.assignPropertyDefs(creatorPropDefs);
    }

    /**
     * Injects synthetic {@code @JsonProperty} annotations for creator parameters that don't already
     * have one. This supports classes compiled with the {@code -parameters} flag where parameter
     * names are available from Jandex but not annotated with {@code @JsonProperty}.
     */
    private static void injectParamNames(AnnotationMap[] paramAnnotations, List<String> paramNames) {
        if (paramAnnotations == null || paramNames == null) {
            return;
        }
        for (int i = 0; i < paramAnnotations.length && i < paramNames.size(); i++) {
            String name = paramNames.get(i);
            if (name == null || name.startsWith("arg")) {
                continue; // synthetic name, skip
            }
            if (paramAnnotations[i].get(JsonProperty.class) != null) {
                continue; // already has @JsonProperty
            }
            paramAnnotations[i].addIfNotPresent(syntheticJsonProperty(name));
        }
    }

    /**
     * Creates a synthetic {@code @JsonProperty} annotation proxy with the given value.
     */
    @SuppressWarnings("all")
    private static JsonProperty syntheticJsonProperty(String name) {
        return new JsonProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonProperty.class;
            }

            @Override
            public String value() {
                return name;
            }

            @Override
            public String namespace() {
                return "";
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public OptBoolean isRequired() {
                return OptBoolean.DEFAULT;
            }

            @Override
            public int index() {
                return -1;
            }

            @Override
            public String defaultValue() {
                return "";
            }

            @Override
            public JsonProperty.Access access() {
                return JsonProperty.Access.AUTO;
            }
        };
    }

    /**
     * Collects annotations from a class and all its superclasses.
     * Subclass annotations take precedence (added first via {@link AnnotationMap#addIfNotPresent}).
     */
    private static AnnotationMap collectClassAnnotations(Class<?> clazz) {
        AnnotationMap map = new AnnotationMap();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Annotation ann : current.getDeclaredAnnotations()) {
                map.addIfNotPresent(ann);
            }
            current = current.getSuperclass();
        }
        return map;
    }

    /**
     * Collects annotations from a single reflect element (field, method, constructor).
     */
    private static AnnotationMap collectAnnotations(AnnotatedElement element) {
        Annotation[] anns = element.getDeclaredAnnotations();
        if (anns.length == 0) {
            return new AnnotationMap();
        }
        AnnotationMap map = new AnnotationMap();
        for (Annotation ann : anns) {
            map.add(ann);
        }
        return map;
    }

    /**
     * Collects parameter annotations for a method (one AnnotationMap per parameter).
     */
    private static AnnotationMap[] collectMethodParameterAnnotations(Method method) {
        Parameter[] params = method.getParameters();
        AnnotationMap[] result = new AnnotationMap[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = collectAnnotations(params[i]);
        }
        return result;
    }

    /**
     * Collects parameter annotations for a constructor (one AnnotationMap per parameter).
     */
    private static AnnotationMap[] collectConstructorParameterAnnotations(Constructor<?> ctor) {
        Parameter[] params = ctor.getParameters();
        AnnotationMap[] result = new AnnotationMap[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = collectAnnotations(params[i]);
        }
        return result;
    }

    private static Class<?> loadClass(ClassLoader cl, String className) throws ClassNotFoundException {
        return switch (className) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            default -> cl.loadClass(className);
        };
    }
}
