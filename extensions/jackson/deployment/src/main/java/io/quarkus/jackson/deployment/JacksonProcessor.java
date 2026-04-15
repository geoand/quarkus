package io.quarkus.jackson.deployment;

import static org.jboss.jandex.AnnotationTarget.Kind.CLASS;
import static org.jboss.jandex.AnnotationTarget.Kind.METHOD;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.RecordComponentInfo;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.SimpleObjectIdResolver;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmo2Adaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.impl.Reflections;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.Reflection2Gizmo;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkus.jackson.JacksonMixin;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.jackson.runtime.ConfigurationCustomizer;
import io.quarkus.jackson.runtime.JacksonBuildTimeConfig;
import io.quarkus.jackson.runtime.JacksonRecorder;
import io.quarkus.jackson.runtime.JacksonSupport;
import io.quarkus.jackson.runtime.ObjectMapperProducer;
import io.quarkus.jackson.runtime.PrecomputedClassMetadata;
import io.quarkus.jackson.runtime.PrecomputedMetadataCustomizer;
import io.quarkus.jackson.runtime.PrecomputedPropertyInfo;
import io.quarkus.jackson.runtime.VertxHybridPoolObjectMapperCustomizer;
import io.quarkus.jackson.spi.ClassPathJacksonModuleBuildItem;
import io.quarkus.jackson.spi.JacksonModuleBuildItem;
import io.quarkus.jackson.spi.PrecomputedJacksonTypeBuildItem;

public class JacksonProcessor {

    private static final DotName JSON_DESERIALIZE = DotName.createSimple(JsonDeserialize.class.getName());

    private static final DotName JSON_SERIALIZE = DotName.createSimple(JsonSerialize.class.getName());

    private static final DotName JSON_AUTO_DETECT = DotName.createSimple(JsonAutoDetect.class.getName());

    private static final DotName JSON_TYPE_ID_RESOLVER = DotName.createSimple(JsonTypeIdResolver.class.getName());
    private static final DotName JSON_SUBTYPES = DotName.createSimple(JsonSubTypes.class.getName());
    private static final DotName JACKSON_NAMING = DotName.createSimple(JsonNaming.class.getName());
    private static final DotName JSON_CREATOR = DotName.createSimple("com.fasterxml.jackson.annotation.JsonCreator");

    private static final DotName JSON_NAMING = DotName.createSimple("com.fasterxml.jackson.databind.annotation.JsonNaming");

    private static final DotName JSON_IDENTITY_INFO = DotName.createSimple("com.fasterxml.jackson.annotation.JsonIdentityInfo");

    private static final DotName BUILDER_VOID = DotName.createSimple(Void.class.getName());

    private static final String TIME_MODULE = "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule";

    private static final String JDK8_MODULE = "com.fasterxml.jackson.datatype.jdk8.Jdk8Module";

    private static final String PARAMETER_NAMES_MODULE = "com.fasterxml.jackson.module.paramnames.ParameterNamesModule";
    private static final DotName JACKSON_MIXIN = DotName.createSimple(JacksonMixin.class.getName());

    private static final MethodDesc OBJECT_MAPPER_REGISTER_MODULE_METHOD_DESC = MethodDesc.of(ObjectMapper.class,
            "registerModule", ObjectMapper.class, Module.class);

    // this list can probably be enriched with more modules
    private static final List<String> MODULES_NAMES_TO_AUTO_REGISTER = Arrays.asList(TIME_MODULE, JDK8_MODULE,
            PARAMETER_NAMES_MODULE);
    private static final String[] EMPTY_STRING = new String[0];

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @Inject
    List<IgnoreJsonDeserializeClassBuildItem> ignoreJsonDeserializeClassBuildItems;

    @BuildStep
    void unremovable(Capabilities capabilities, BuildProducer<UnremovableBeanBuildItem> producer,
            BuildProducer<AdditionalBeanBuildItem> additionalProducer) {
        additionalProducer.produce(AdditionalBeanBuildItem.unremovableOf(ConfigurationCustomizer.class));

        if (capabilities.isPresent(Capability.VERTX_CORE)) {
            producer.produce(UnremovableBeanBuildItem.beanTypes(ObjectMapper.class));
            additionalProducer.produce(AdditionalBeanBuildItem.unremovableOf(VertxHybridPoolObjectMapperCustomizer.class));
        }
    }

    @BuildStep
    void register(
            CurateOutcomeBuildItem curateOutcomeBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyClass,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethod,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder("com.fasterxml.jackson.databind.ser.std.SqlDateSerializer",
                        "com.fasterxml.jackson.databind.ser.std.SqlTimeSerializer",
                        "com.fasterxml.jackson.databind.deser.std.DateDeserializers$SqlDateDeserializer",
                        "com.fasterxml.jackson.databind.deser.std.DateDeserializers$TimestampDeserializer",
                        "com.fasterxml.jackson.annotation.SimpleObjectIdResolver")
                        .reason(getClass().getName())
                        .methods().build());
        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder(
                        "com.fasterxml.jackson.databind.ser.std.ClassSerializer",
                        "com.fasterxml.jackson.databind.ext.CoreXMLSerializers",
                        "com.fasterxml.jackson.databind.ext.CoreXMLDeserializers")
                        .reason(getClass().getName())
                        .constructors()
                        .build());

        if (curateOutcomeBuildItem.getApplicationModel().getDependencies().stream().anyMatch(
                x -> x.getGroupId().equals("com.fasterxml.jackson.module")
                        && x.getArtifactId().equals("jackson-module-jaxb-annotations"))) {
            reflectiveClass.produce(
                    ReflectiveClassBuildItem.builder("com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector")
                            .reason(getClass().getName())
                            .methods().build());
        }

        IndexView index = combinedIndexBuildItem.getIndex();

        // TODO: @JsonDeserialize is only supported as a class annotation - we should support the others as well

        Set<DotName> ignoredDotNames = new HashSet<>();
        for (IgnoreJsonDeserializeClassBuildItem ignoreJsonDeserializeClassBuildItem : ignoreJsonDeserializeClassBuildItems) {
            ignoredDotNames.addAll(ignoreJsonDeserializeClassBuildItem.getDotNames());
        }

        // handle the various @JsonDeserialize cases
        for (AnnotationInstance deserializeInstance : index.getAnnotations(JSON_DESERIALIZE)) {
            AnnotationTarget annotationTarget = deserializeInstance.target();
            if (CLASS.equals(annotationTarget.kind())) {
                DotName dotName = annotationTarget.asClass().name();
                if (!ignoredDotNames.contains(dotName)) {
                    addReflectiveHierarchyClass(getClass().getSimpleName() + " annotated with @" + JSON_DESERIALIZE,
                            dotName, reflectiveHierarchyClass);
                }

                AnnotationValue annotationValue = deserializeInstance.value("builder");
                if (null != annotationValue && AnnotationValue.Kind.CLASS.equals(annotationValue.kind())) {
                    DotName builderClassName = annotationValue.asClass().name();
                    if (!BUILDER_VOID.equals(builderClassName)) {
                        addReflectiveHierarchyClass(
                                getClass().getSimpleName() + " @" + JSON_DESERIALIZE + " builder of " + dotName,
                                builderClassName, reflectiveHierarchyClass);
                    }
                }
            }
            AnnotationValue usingValue = deserializeInstance.value("using");
            if (usingValue != null) {
                // the Deserializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(usingValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_DESERIALIZE + " using")
                        .build());
            }
            AnnotationValue keyUsingValue = deserializeInstance.value("keyUsing");
            if (keyUsingValue != null) {
                // the Deserializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(keyUsingValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_DESERIALIZE + " keyUsing")
                        .build());
            }
            AnnotationValue contentUsingValue = deserializeInstance.value("contentUsing");
            if (contentUsingValue != null) {
                // the Deserializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(contentUsingValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_DESERIALIZE + " contentUsing")
                        .build());
            }
        }

        // handle the various @JsonSerialize cases
        for (AnnotationInstance serializeInstance : index.getAnnotations(JSON_SERIALIZE)) {
            AnnotationValue usingValue = serializeInstance.value("using");
            if (usingValue != null) {
                // the Serializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(usingValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_SERIALIZE + " using")
                        .build());
            }
            AnnotationValue keyUsingValue = serializeInstance.value("keyUsing");
            if (keyUsingValue != null) {
                // the Deserializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(keyUsingValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_SERIALIZE + " keyUsing")
                        .build());
            }
            AnnotationValue contentUsingValue = serializeInstance.value("contentUsing");
            if (contentUsingValue != null) {
                // the Deserializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass
                        .produce(ReflectiveClassBuildItem.builder(contentUsingValue.asClass().name().toString())
                                .reason(getClass().getName() + " @" + JSON_SERIALIZE + " contentUsing")
                                .build());
            }
            AnnotationValue nullsUsingValue = serializeInstance.value("nullsUsing");
            if (nullsUsingValue != null) {
                // the Deserializers are constructed internally by Jackson using a no-args constructor
                reflectiveClass
                        .produce(ReflectiveClassBuildItem.builder(nullsUsingValue.asClass().name().toString())
                                .reason(getClass().getName() + " @" + JSON_SERIALIZE + " nullsUsing")
                                .build());
            }
        }

        for (AnnotationInstance creatorInstance : index.getAnnotations(JSON_AUTO_DETECT)) {
            if (creatorInstance.target().kind() == CLASS) {
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(creatorInstance.target().asClass().name().toString())
                        .reason(getClass().getName() + " annotated with @" + JSON_AUTO_DETECT)
                        .methods().fields().build());
            }
        }

        // Register @JsonTypeIdResolver implementations for reflection.
        // Note: @JsonTypeIdResolver is, simply speaking, the "dynamic version" of @JsonSubTypes, i.e. sub-types are
        // dynamically identified by Jackson's `TypeIdResolver.typeFromId()`, which returns sub-types of the annotated
        // class. Means: the referenced `TypeIdResolver` _and_ all sub-types of the annotated class must be registered
        // for reflection.
        for (AnnotationInstance resolverInstance : index.getAnnotations(JSON_TYPE_ID_RESOLVER)) {
            AnnotationValue value = resolverInstance.value("value");
            if (value != null) {
                // Add the type-id-resolver class
                reflectiveClass
                        .produce(ReflectiveClassBuildItem.builder(value.asClass().name().toString()).methods().fields()
                                .reason(getClass().getName() + " @" + JSON_TYPE_ID_RESOLVER + " value")
                                .build());
                if (resolverInstance.target().kind() == CLASS) {
                    // Add the whole hierarchy of the annotated class
                    addReflectiveHierarchyClass(getClass().getSimpleName() + " annotated with @" + JSON_TYPE_ID_RESOLVER,
                            resolverInstance.target().asClass().name(), reflectiveHierarchyClass);
                }
            }
        }

        // make sure we register the constructors and methods marked with @JsonCreator for reflection
        for (AnnotationInstance creatorInstance : index.getAnnotations(JSON_CREATOR)) {
            if (METHOD == creatorInstance.target().kind()) {
                reflectiveMethod
                        .produce(new ReflectiveMethodBuildItem(getClass().getName(), creatorInstance.target().asMethod()));
            }
        }

        // register @JsonNaming strategy implementations for reflection
        for (AnnotationInstance jsonNamingInstance : index.getAnnotations(JSON_NAMING)) {
            AnnotationValue strategyValue = jsonNamingInstance.value("value");
            if (strategyValue != null) {
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(strategyValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_NAMING + " value")
                        .methods().fields().build());
            }
        }

        // register @JsonIdentityInfo strategy implementations for reflection
        for (AnnotationInstance jsonIdentityInfoInstance : index.getAnnotations(JSON_IDENTITY_INFO)) {
            AnnotationValue generatorValue = jsonIdentityInfoInstance.value("generator");
            AnnotationValue resolverValue = jsonIdentityInfoInstance.value("resolver");
            if (generatorValue != null) {
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(generatorValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_IDENTITY_INFO + " generator")
                        .methods().fields().build());
            }
            if (resolverValue != null) {
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(resolverValue.asClass().name().toString())
                        .reason(getClass().getName() + " @" + JSON_IDENTITY_INFO + " resolver")
                        .methods().fields().build());
            } else {
                // Registering since SimpleObjectIdResolver is the default value of @JsonIdentityInfo.resolver
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(SimpleObjectIdResolver.class)
                        .reason(getClass().getName() + " @" + JSON_IDENTITY_INFO + " resolver default value")
                        .methods().fields().build());
            }
        }

        // register @JsonSubTypes.Type values for reflection
        Set<String> subTypeTypesNames = new HashSet<>();
        for (AnnotationInstance subTypeInstance : index.getAnnotations(JSON_SUBTYPES)) {
            AnnotationValue subTypeValue = subTypeInstance.value();
            if (subTypeValue != null) {
                for (AnnotationInstance subTypeTypeInstance : subTypeValue.asNestedArray()) {
                    AnnotationValue subTypeTypeValue = subTypeTypeInstance.value();
                    if (subTypeTypeValue != null) {
                        subTypeTypesNames.add(subTypeTypeValue.asClass().name().toString());
                    }
                }

            }
        }
        if (!subTypeTypesNames.isEmpty()) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(subTypeTypesNames.toArray(EMPTY_STRING))
                    .reason(getClass().getName() + " @" + JSON_SUBTYPES + " value")
                    .methods().fields().build());
        }

        // register @JsonNaming for reflection
        Set<String> namingTypesNames = new HashSet<>();
        for (AnnotationInstance namingInstance : index.getAnnotations(JSON_NAMING)) {
            AnnotationValue namingValue = namingInstance.value();
            if (namingValue != null) {
                namingTypesNames.add(namingValue.asClass().name().toString());
            }
        }
        if (!namingTypesNames.isEmpty()) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(namingTypesNames.toArray(EMPTY_STRING))
                    .reason(getClass().getName() + " @" + JACKSON_NAMING + " value")
                    .build());
        }

        // this needs to be registered manually since the runtime module is not indexed by Jandex
        additionalBeans.produce(new AdditionalBeanBuildItem(ObjectMapperProducer.class));
    }

    private void addReflectiveHierarchyClass(String reason, DotName className,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyClass) {
        reflectiveHierarchyClass.produce(ReflectiveHierarchyBuildItem.builder(className)
                .source(reason)
                .build());
    }

    @BuildStep
    void autoRegisterModules(BuildProducer<ClassPathJacksonModuleBuildItem> classPathJacksonModules) {
        for (String module : MODULES_NAMES_TO_AUTO_REGISTER) {
            registerModuleIfOnClassPath(module, classPathJacksonModules);
        }
    }

    private void registerModuleIfOnClassPath(String moduleClassName,
            BuildProducer<ClassPathJacksonModuleBuildItem> classPathJacksonModules) {
        if (QuarkusClassLoader.isClassPresentAtRuntime(moduleClassName)) {
            classPathJacksonModules.produce(new ClassPathJacksonModuleBuildItem(moduleClassName));
        }
    }

    // Generate a ObjectMapperCustomizer bean that registers each serializer / deserializer as well as detected modules with the ObjectMapper
    @BuildStep
    void generateCustomizer(BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            List<JacksonModuleBuildItem> jacksonModules, List<ClassPathJacksonModuleBuildItem> classPathJacksonModules) {

        if (jacksonModules.isEmpty() && classPathJacksonModules.isEmpty()) {
            return;
        }

        ClassOutput classOutput = new GeneratedBeanGizmo2Adaptor(generatedBeans);
        Gizmo g = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);
        g.class_("io.quarkus.jackson.customizer.RegisterSerializersAndDeserializersCustomizer", cc -> {
            cc.implements_(ObjectMapperCustomizer.class);
            cc.defaultConstructor();
            cc.addAnnotation(Singleton.class);
            cc.method("customize", mc -> {
                ParamVar objectMapperParam = mc.parameter("objectMapper", ObjectMapper.class);
                mc.returning(void.class);
                mc.body(bc -> {
                    ClassDesc simpleModuleClassDesc = Reflection2Gizmo.classDescOf(SimpleModule.class);
                    for (JacksonModuleBuildItem jacksonModule : jacksonModules) {
                        if (jacksonModule.getItems().isEmpty()) {
                            continue;
                        }

                        /*
                         * Create code similar to the following:
                         *
                         * SimpleModule module = new SimpleModule("somename");
                         * module.addSerializer(Foo.class, new FooSerializer());
                         * module.addDeserializer(Foo.class, new FooDeserializer());
                         * objectMapper.registerModule(module);
                         */

                        LocalVar simpleModuleInstance = bc.localVar("simpleModule",
                                bc.new_(SimpleModule.class, Const.of(jacksonModule.getName())));

                        for (JacksonModuleBuildItem.Item item : jacksonModule.getItems()) {

                            LocalVar targetClass = bc.localVar("targetClass",
                                    Const.of(ClassDesc.of(item.getTargetClassName())));
                            String serializerClassName = item.getSerializerClassName();
                            if ((serializerClassName != null) && !serializerClassName.isEmpty()) {
                                ClassDesc serializerClassDesc = ClassDesc.of(serializerClassName);
                                Expr serializerInstance = bc.new_(serializerClassDesc);

                                bc.invokeVirtual(
                                        ClassMethodDesc.of(simpleModuleClassDesc, "addSerializer",
                                                MethodTypeDesc.of(simpleModuleClassDesc,
                                                        ConstantDescs.CD_Class, Reflection2Gizmo.classDescOf(
                                                                JsonSerializer.class))),
                                        simpleModuleInstance, targetClass, serializerInstance);

                            }

                            String deserializerClassName = item.getDeserializerClassName();
                            if ((deserializerClassName != null) && !deserializerClassName.isEmpty()) {
                                ClassDesc deserializerClassDesc = ClassDesc.of(deserializerClassName);
                                Expr deserializerInstance = bc.new_(deserializerClassDesc);

                                bc.invokeVirtual(
                                        ClassMethodDesc.of(simpleModuleClassDesc, "addDeserializer",
                                                MethodTypeDesc.of(simpleModuleClassDesc,
                                                        ConstantDescs.CD_Class, Reflection2Gizmo.classDescOf(
                                                                JsonDeserializer.class))),
                                        simpleModuleInstance, targetClass, deserializerInstance);

                            }
                        }

                        bc.invokeVirtual(
                                OBJECT_MAPPER_REGISTER_MODULE_METHOD_DESC,
                                objectMapperParam, simpleModuleInstance);

                    }

                    for (ClassPathJacksonModuleBuildItem classPathJacksonModule : classPathJacksonModules) {
                        bc.invokeVirtual(
                                OBJECT_MAPPER_REGISTER_MODULE_METHOD_DESC,
                                objectMapperParam, bc.new_(ClassDesc.of(classPathJacksonModule.getModuleClassName())));
                    }

                    bc.return_();
                });
            });
            cc.method("priority", mc -> {
                mc.returning(int.class);
                mc.body(bc -> bc.return_(ObjectMapperCustomizer.QUARKUS_CUSTOMIZER_PRIORITY));
            });
        });
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    public void supportMixins(JacksonRecorder recorder,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        IndexView index = combinedIndexBuildItem.getIndex();
        Collection<AnnotationInstance> jacksonMixins = index.getAnnotations(JACKSON_MIXIN);
        if (jacksonMixins.isEmpty()) {
            return;
        }

        Map<Class<?>, Class<?>> mixinsMap = new HashMap<>();
        for (AnnotationInstance instance : jacksonMixins) {
            if (instance.target().kind() != CLASS) {
                continue;
            }
            ClassInfo mixinClassInfo = instance.target().asClass();
            String mixinClassName = mixinClassInfo.name().toString();
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(mixinClassName)
                    .reason(getClass().getName() + " annotated with @" + JACKSON_MIXIN)
                    .methods().fields().build());
            try {
                Type[] targetTypes = instance.value().asClassArray();
                if ((targetTypes == null) || targetTypes.length == 0) {
                    continue;
                }
                Class<?> mixinClass = Thread.currentThread().getContextClassLoader().loadClass(mixinClassName);
                for (Type targetType : targetTypes) {
                    String targetClassName = targetType.name().toString();
                    reflectiveClass.produce(ReflectiveClassBuildItem.builder(targetClassName)
                            .reason(getClass().getName() + " @" + JACKSON_MIXIN + " value of " + mixinClassName)
                            .methods().fields().build());
                    mixinsMap.put(Thread.currentThread().getContextClassLoader().loadClass(targetClassName),
                            mixinClass);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unable to determine Jackson mixin usage at build", e);
            }
        }
        if (mixinsMap.isEmpty()) {
            return;
        }
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(ObjectMapperCustomizer.class)
                .scope(Singleton.class)
                .supplier(recorder.customizerSupplier(mixinsMap))
                .done());
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    public SyntheticBeanBuildItem jacksonSupport(JacksonRecorder recorder,
            JacksonBuildTimeConfig jacksonBuildTimeConfig) {
        return SyntheticBeanBuildItem
                .configure(JacksonSupport.class)
                .scope(Singleton.class)
                .supplier(recorder.supplier(determinePropertyNamingStrategyClassName(jacksonBuildTimeConfig)))
                .done();
    }

    /**
     * Registers {@link PrecomputedMetadataCustomizer} as a CDI bean early in the build chain.
     * This avoids a cycle: REST endpoint discovery produces {@code PrecomputedJacksonTypeBuildItem}
     * which feeds into {@code precomputeJacksonMetadata}; if that step produced a
     * {@code SyntheticBeanBuildItem}, it would cycle back through bean registration
     * to the endpoint discovery step.
     */
    @BuildStep(onlyIf = JacksonBuildTimeConfig.IsBuildTimeIntrospectionEnabled.class)
    void registerPrecomputedMetadataBean(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(PrecomputedMetadataCustomizer.class)
                .setUnremovable().setDefaultScope(
                        DotNames.SINGLETON)
                .build());
    }

    private static final DotName JSON_IGNORE = DotName.createSimple("com.fasterxml.jackson.annotation.JsonIgnore");
    private static final DotName JSON_PROPERTY = DotName.createSimple("com.fasterxml.jackson.annotation.JsonProperty");
    private static final DotName JSON_GETTER = DotName.createSimple("com.fasterxml.jackson.annotation.JsonGetter");
    private static final DotName JSON_SETTER = DotName.createSimple("com.fasterxml.jackson.annotation.JsonSetter");
    private static final DotName JSON_VALUE = DotName.createSimple("com.fasterxml.jackson.annotation.JsonValue");
    private static final DotName JSON_ANY_GETTER = DotName.createSimple("com.fasterxml.jackson.annotation.JsonAnyGetter");
    private static final DotName JSON_ANY_SETTER = DotName.createSimple("com.fasterxml.jackson.annotation.JsonAnySetter");
    private static final DotName JSON_IGNORE_PROPERTIES = DotName
            .createSimple("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
    private static final DotName JSON_VIEW = DotName.createSimple("com.fasterxml.jackson.annotation.JsonView");
    private static final DotName JSON_TYPE_INFO = DotName.createSimple("com.fasterxml.jackson.annotation.JsonTypeInfo");
    private static final DotName SECURE_FIELD = DotName
            .createSimple("io.quarkus.resteasy.reactive.jackson.SecureField");
    private static final DotName OBJECT = DotName.createSimple("java.lang.Object");

    /**
     * Collects types nominated for precomputed Jackson metadata (via {@link PrecomputedJacksonTypeBuildItem}),
     * extracts their structure from Jandex — properties (fields, getters, setters),
     * {@code @JsonCreator} constructors, {@code @JsonValue}/{@code @JsonAnyGetter}/{@code @JsonAnySetter}
     * methods, {@code @JsonIgnoreProperties}, and annotation-driven property discovery
     * ({@code @JsonGetter}, {@code @JsonSetter}, {@code @JsonProperty} on arbitrary methods)
     * — and passes it to the recorder.
     */
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(onlyIf = JacksonBuildTimeConfig.IsBuildTimeIntrospectionEnabled.class)
    public void precomputeJacksonMetadata(JacksonRecorder recorder,
            CombinedIndexBuildItem combinedIndex,
            List<PrecomputedJacksonTypeBuildItem> precomputedTypes) {
        if (precomputedTypes.isEmpty()) {
            return;
        }

        IndexView index = combinedIndex.getIndex();
        Map<String, PrecomputedClassMetadata> classToMetadata = new LinkedHashMap<>();
        for (PrecomputedJacksonTypeBuildItem item : precomputedTypes) {
            ClassInfo classInfo = item.getClassInfo();
            PrecomputedClassMetadata metadata = discoverClassMetadata(classInfo, index);
            classToMetadata.put(classInfo.name().toString(), metadata);
        }

        recorder.setPrecomputedMetadata(classToMetadata);
    }

    /**
     * Discovers all Jackson metadata for a class from Jandex: properties (fields, getters, setters),
     * {@code @JsonCreator} constructor, {@code @JsonValue}, {@code @JsonAnyGetter}/{@code @JsonAnySetter},
     * and {@code @JsonIgnoreProperties}.
     * <p>
     * Walks the class hierarchy to include inherited members.
     */
    private static PrecomputedClassMetadata discoverClassMetadata(ClassInfo classInfo, IndexView index) {
        // Records have different property discovery: component accessors + canonical constructor
        if (classInfo.isRecord()) {
            return discoverRecordMetadata(classInfo, index);
        }

        // fieldMap: propName -> [fieldName, declaringClassName]
        Map<String, String[]> fieldMap = new LinkedHashMap<>();
        Map<String, String> getterMap = new LinkedHashMap<>();
        Map<String, String[]> setterMap = new LinkedHashMap<>();
        Set<String> ignoredProperties = new HashSet<>();
        Map<String, String> jsonPropertyNames = new LinkedHashMap<>();

        // Class-level metadata discovered while walking
        String jsonValueMethodName = null;
        String jsonValueFieldName = null;
        String jsonValueFieldDeclaringClass = null;
        String anyGetterMethodName = null;
        String anySetterMethodName = null;
        String anySetterParamType = null;
        List<String> creatorConstructorParamTypes = null;
        List<String> creatorConstructorParamNames = null;
        String creatorFactoryMethodName = null;
        String creatorFactoryMethodDeclaringClass = null;
        List<String> creatorFactoryMethodParamTypes = null;
        List<String> creatorFactoryMethodParamNames = null;
        List<String> classLevelIgnoredProperties = new ArrayList<>();

        // Read @JsonAutoDetect visibility — walk hierarchy to find first occurrence
        JsonAutoDetect.Visibility fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY;
        JsonAutoDetect.Visibility getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY;
        JsonAutoDetect.Visibility isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY;
        JsonAutoDetect.Visibility setterVisibility = JsonAutoDetect.Visibility.ANY;
        {
            ClassInfo vis = classInfo;
            while (vis != null && !OBJECT.equals(vis.name())) {
                AnnotationInstance autoDetect = vis.annotation(JSON_AUTO_DETECT);
                if (autoDetect != null) {
                    AnnotationValue fv = autoDetect.value("fieldVisibility");
                    if (fv != null) {
                        fieldVisibility = JsonAutoDetect.Visibility.valueOf(fv.asEnum());
                    }
                    AnnotationValue gv = autoDetect.value("getterVisibility");
                    if (gv != null) {
                        getterVisibility = JsonAutoDetect.Visibility.valueOf(gv.asEnum());
                    }
                    AnnotationValue igv = autoDetect.value("isGetterVisibility");
                    if (igv != null) {
                        isGetterVisibility = JsonAutoDetect.Visibility.valueOf(igv.asEnum());
                    }
                    AnnotationValue sv = autoDetect.value("setterVisibility");
                    if (sv != null) {
                        setterVisibility = JsonAutoDetect.Visibility.valueOf(sv.asEnum());
                    }
                    break; // first @JsonAutoDetect wins
                }
                vis = vis.superName() != null ? index.getClassByName(vis.superName()) : null;
            }
        }

        // Walk the class hierarchy (subclass members take precedence via putIfAbsent)
        ClassInfo current = classInfo;
        while (current != null && !OBJECT.equals(current.name())) {
            collectMembers(current, fieldMap, getterMap, setterMap, ignoredProperties, jsonPropertyNames,
                    fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility);

            // @JsonIgnoreProperties (class-level) — collect from all levels
            AnnotationInstance ignorePropsAnn = current.annotation(JSON_IGNORE_PROPERTIES);
            if (ignorePropsAnn != null) {
                AnnotationValue valueAttr = ignorePropsAnn.value();
                if (valueAttr != null) {
                    for (String ignored : valueAttr.asStringArray()) {
                        classLevelIgnoredProperties.add(ignored);
                    }
                }
            }

            // @JsonCreator — find annotated constructor or static factory method (first one wins, subclass first)
            if (creatorConstructorParamTypes == null && creatorFactoryMethodName == null) {
                for (MethodInfo method : current.methods()) {
                    if (!method.hasAnnotation(JSON_CREATOR)) {
                        continue;
                    }
                    if ("<init>".equals(method.name())) {
                        creatorConstructorParamTypes = new ArrayList<>();
                        creatorConstructorParamNames = new ArrayList<>();
                        for (int i = 0; i < method.parametersCount(); i++) {
                            creatorConstructorParamTypes.add(method.parameterType(i).name().toString());
                            creatorConstructorParamNames.add(method.parameterName(i));
                        }
                        extractCtorJsonPropertyNames(method, jsonPropertyNames);
                        break;
                    } else if (Modifier.isStatic(method.flags())) {
                        creatorFactoryMethodName = method.name();
                        creatorFactoryMethodDeclaringClass = current.name().toString();
                        creatorFactoryMethodParamTypes = new ArrayList<>();
                        creatorFactoryMethodParamNames = new ArrayList<>();
                        for (int i = 0; i < method.parametersCount(); i++) {
                            creatorFactoryMethodParamTypes.add(method.parameterType(i).name().toString());
                            creatorFactoryMethodParamNames.add(method.parameterName(i));
                        }
                        extractCtorJsonPropertyNames(method, jsonPropertyNames);
                        break;
                    }
                }
            }

            // @JsonValue — find annotated method or field (first one wins, subclass first)
            if (jsonValueMethodName == null && jsonValueFieldName == null) {
                for (MethodInfo method : current.methods()) {
                    if (method.hasAnnotation(JSON_VALUE) && method.parametersCount() == 0
                            && method.returnType().kind() != Type.Kind.VOID) {
                        jsonValueMethodName = method.name();
                        break;
                    }
                }
                if (jsonValueMethodName == null) {
                    for (FieldInfo field : current.fields()) {
                        if (field.hasAnnotation(JSON_VALUE)) {
                            jsonValueFieldName = field.name();
                            jsonValueFieldDeclaringClass = current.name().toString();
                            break;
                        }
                    }
                }
            }

            // @JsonAnyGetter — find annotated method
            if (anyGetterMethodName == null) {
                for (MethodInfo method : current.methods()) {
                    if (method.hasAnnotation(JSON_ANY_GETTER) && method.parametersCount() == 0) {
                        anyGetterMethodName = method.name();
                        break;
                    }
                }
            }

            // @JsonAnySetter — find annotated method
            if (anySetterMethodName == null) {
                for (MethodInfo method : current.methods()) {
                    if (method.hasAnnotation(JSON_ANY_SETTER) && method.parametersCount() == 2) {
                        anySetterMethodName = method.name();
                        anySetterParamType = method.parameterType(1).name().toString();
                        break;
                    }
                }
            }

            if (current.superName() != null) {
                current = index.getClassByName(current.superName());
            } else {
                break;
            }
        }

        // Auto-detect creator constructor when no @JsonCreator was found.
        // Matches Jackson's standard behavior with ParameterNamesModule:
        //  1) constructor with @JsonProperty on any parameter → explicit creator
        //  2) single non-default constructor when no default exists → implicit creator
        if (creatorConstructorParamTypes == null && creatorFactoryMethodName == null) {
            boolean hasDefaultCtor = hasDefaultConstructor(classInfo);
            MethodInfo autoDetectedCtor = null;

            List<MethodInfo> nonDefaultCtors = new ArrayList<>();
            for (MethodInfo method : classInfo.methods()) {
                if (!"<init>".equals(method.name()) || method.parametersCount() == 0) {
                    continue;
                }
                nonDefaultCtors.add(method);

                // Priority 1: constructor with @JsonProperty on any parameter
                if (autoDetectedCtor == null) {
                    for (AnnotationInstance ann : method.annotations()) {
                        if (ann.name().equals(JSON_PROPERTY)
                                && ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                            autoDetectedCtor = method;
                            break;
                        }
                    }
                }
            }

            // Priority 2: no default constructor and single non-default constructor
            if (autoDetectedCtor == null && !hasDefaultCtor && nonDefaultCtors.size() == 1) {
                autoDetectedCtor = nonDefaultCtors.get(0);
            }

            if (autoDetectedCtor != null) {
                creatorConstructorParamTypes = new ArrayList<>();
                creatorConstructorParamNames = new ArrayList<>();
                for (int i = 0; i < autoDetectedCtor.parametersCount(); i++) {
                    creatorConstructorParamTypes.add(autoDetectedCtor.parameterType(i).name().toString());
                    creatorConstructorParamNames.add(autoDetectedCtor.parameterName(i));
                }
                extractCtorJsonPropertyNames(autoDetectedCtor, jsonPropertyNames);
            }
        }

        // Add class-level @JsonIgnoreProperties to the ignored set
        ignoredProperties.addAll(classLevelIgnoredProperties);

        // Correlate fields/getters/setters into properties
        Set<String> allPropertyNames = new LinkedHashSet<>();
        allPropertyNames.addAll(fieldMap.keySet());
        allPropertyNames.addAll(getterMap.keySet());
        allPropertyNames.addAll(setterMap.keySet());

        List<PrecomputedPropertyInfo> properties = new ArrayList<>();
        for (String propName : allPropertyNames) {
            if (ignoredProperties.contains(propName)) {
                continue;
            }
            String[] fieldInfo = fieldMap.get(propName);
            String getterName = getterMap.get(propName);
            String[] setterInfo = setterMap.get(propName);

            String effectiveName = jsonPropertyNames.getOrDefault(propName, propName);
            String declaringClassName = fieldInfo != null ? fieldInfo[1] : classInfo.name().toString();

            properties.add(new PrecomputedPropertyInfo(effectiveName, declaringClassName,
                    fieldInfo != null ? fieldInfo[0] : null, getterName,
                    setterInfo != null ? setterInfo[0] : null,
                    setterInfo != null ? setterInfo[1] : null));
        }

        return new PrecomputedClassMetadata(
                properties,
                creatorConstructorParamTypes,
                creatorConstructorParamNames,
                creatorFactoryMethodName,
                creatorFactoryMethodDeclaringClass,
                creatorFactoryMethodParamTypes,
                creatorFactoryMethodParamNames,
                jsonValueMethodName,
                jsonValueFieldName,
                jsonValueFieldDeclaringClass,
                anyGetterMethodName,
                anySetterMethodName,
                anySetterParamType,
                classLevelIgnoredProperties.isEmpty() ? null : classLevelIgnoredProperties);
    }

    /**
     * Discovers metadata for a Java record: record component accessors as getters,
     * the canonical constructor as the creator, and class-level annotations.
     */
    private static PrecomputedClassMetadata discoverRecordMetadata(ClassInfo classInfo, IndexView index) {
        List<RecordComponentInfo> components = classInfo.recordComponentsInDeclarationOrder();
        String className = classInfo.name().toString();

        List<PrecomputedPropertyInfo> properties = new ArrayList<>();
        List<String> creatorParamTypes = new ArrayList<>();
        List<String> creatorParamNames = new ArrayList<>();
        Map<String, String> jsonPropertyNames = new LinkedHashMap<>();

        for (RecordComponentInfo component : components) {
            String compName = component.name();
            String compType = component.type().name().toString();

            creatorParamTypes.add(compType);
            creatorParamNames.add(compName);

            // Check for @JsonProperty name override on the component's accessor
            FieldInfo field = classInfo.field(compName);
            String effectiveName = compName;
            if (field != null) {
                AnnotationInstance jsonProp = field.annotation(JSON_PROPERTY);
                if (jsonProp != null && jsonProp.value() != null && !jsonProp.value().asString().isEmpty()) {
                    effectiveName = jsonProp.value().asString();
                }
            }

            // Record component accessor method has the same name as the component
            properties.add(new PrecomputedPropertyInfo(effectiveName, className,
                    compName, compName, null, null));
        }

        // Collect @JsonIgnoreProperties
        List<String> ignoredProperties = new ArrayList<>();
        AnnotationInstance ignorePropsAnn = classInfo.annotation(JSON_IGNORE_PROPERTIES);
        if (ignorePropsAnn != null) {
            AnnotationValue valueAttr = ignorePropsAnn.value();
            if (valueAttr != null) {
                ignoredProperties.addAll(List.of(valueAttr.asStringArray()));
            }
        }

        return new PrecomputedClassMetadata(
                properties,
                creatorParamTypes,
                creatorParamNames,
                null, null, null, null, // no factory method
                null, null, null, // no @JsonValue
                null, null, null, // no @JsonAnyGetter/@JsonAnySetter
                ignoredProperties.isEmpty() ? null : ignoredProperties);
    }

    /**
     * Collects fields and methods from a single class level.
     * Handles standard JavaBean naming (getX/isX/setX) as well as annotation-driven
     * discovery ({@code @JsonGetter}, {@code @JsonSetter}, {@code @JsonProperty} on
     * arbitrary methods).
     * Uses {@code putIfAbsent} so subclass members take precedence when walking up.
     */
    private static void collectMembers(ClassInfo classInfo,
            Map<String, String[]> fieldMap,
            Map<String, String> getterMap,
            Map<String, String[]> setterMap,
            Set<String> ignoredProperties,
            Map<String, String> jsonPropertyNames,
            JsonAutoDetect.Visibility fieldVisibility,
            JsonAutoDetect.Visibility getterVisibility,
            JsonAutoDetect.Visibility isGetterVisibility,
            JsonAutoDetect.Visibility setterVisibility) {

        // Fields
        for (FieldInfo field : classInfo.fields()) {
            int flags = field.flags();
            if (Modifier.isStatic(flags) || Modifier.isTransient(flags)) {
                continue;
            }
            String propName = field.name();

            if (field.hasAnnotation(JSON_IGNORE)) {
                ignoredProperties.add(propName);
                continue;
            }

            // Always record the field — even non-visible private fields need to be
            // tracked so their annotations (e.g. @SecureField, @JsonView) can be merged
            // into the getter/setter at runtime. This matches Jackson's POJOPropertiesCollector
            // which adds ALL fields to the property builder regardless of visibility.
            fieldMap.putIfAbsent(propName, new String[] { field.name(), classInfo.name().toString() });

            var jsonProp = field.annotation(JSON_PROPERTY);
            if (jsonProp != null && jsonProp.value() != null && !jsonProp.value().asString().isEmpty()) {
                jsonPropertyNames.putIfAbsent(propName, jsonProp.value().asString());
            }
        }

        // Methods
        for (MethodInfo method : classInfo.methods()) {
            if (Modifier.isStatic(method.flags())) {
                continue;
            }
            if ("<init>".equals(method.name()) || "<clinit>".equals(method.name())) {
                continue;
            }
            String name = method.name();

            if (method.hasAnnotation(JSON_IGNORE)) {
                // Determine property name for this method to add to ignored set
                String propName = derivePropertyNameFromMethod(method);
                if (propName != null) {
                    ignoredProperties.add(propName);
                }
                continue;
            }

            // Skip @JsonValue, @JsonAnyGetter, @JsonAnySetter — handled at class level
            if (method.hasAnnotation(JSON_VALUE) || method.hasAnnotation(JSON_ANY_GETTER)
                    || method.hasAnnotation(JSON_ANY_SETTER)) {
                continue;
            }

            boolean isGetter = false;
            boolean isSetter = false;
            String propName = null;

            // 1. Check @JsonGetter annotation
            if (method.hasAnnotation(JSON_GETTER) && method.parametersCount() == 0
                    && method.returnType().kind() != Type.Kind.VOID) {
                isGetter = true;
                propName = getAnnotationStringValue(method.annotation(JSON_GETTER));
                if (propName == null) {
                    // Fall back to standard naming derivation
                    propName = deriveGetterPropertyName(name, method);
                    if (propName == null) {
                        propName = name; // use raw method name as last resort
                    }
                }
            }

            // 2. Check @JsonSetter annotation
            if (!isGetter && method.hasAnnotation(JSON_SETTER) && method.parametersCount() == 1) {
                isSetter = true;
                propName = getAnnotationStringValue(method.annotation(JSON_SETTER));
                if (propName == null) {
                    propName = deriveSetterPropertyName(name);
                    if (propName == null) {
                        propName = name;
                    }
                }
            }

            // 3. Check @JsonProperty annotation on non-standard methods
            if (!isGetter && !isSetter && method.hasAnnotation(JSON_PROPERTY)) {
                var jsonPropAnn = method.annotation(JSON_PROPERTY);
                String explicitName = getAnnotationStringValue(jsonPropAnn);

                if (method.parametersCount() == 0 && method.returnType().kind() != Type.Kind.VOID) {
                    // Zero-param, non-void return → getter
                    isGetter = true;
                    propName = explicitName;
                    if (propName == null) {
                        propName = deriveGetterPropertyName(name, method);
                        if (propName == null) {
                            propName = name;
                        }
                    }
                } else if (method.parametersCount() == 1) {
                    // One-param → setter
                    isSetter = true;
                    propName = explicitName;
                    if (propName == null) {
                        propName = deriveSetterPropertyName(name);
                        if (propName == null) {
                            propName = name;
                        }
                    }
                }
            }

            // 4. Standard JavaBean naming conventions (getX/isX/setX) — subject to visibility rules
            if (!isGetter && !isSetter) {
                int flags = method.flags();
                if (method.parametersCount() == 0 && method.returnType().kind() != Type.Kind.VOID) {
                    propName = deriveGetterPropertyName(name, method);
                    if (propName != null) {
                        // is-getters use isGetterVisibility, regular getters use getterVisibility
                        JsonAutoDetect.Visibility vis = name.startsWith("is") ? isGetterVisibility : getterVisibility;
                        if (isVisible(flags, vis)) {
                            isGetter = true;
                        } else {
                            propName = null;
                        }
                    }
                }
                if (!isGetter && name.startsWith("set") && name.length() > 3 && method.parametersCount() == 1) {
                    if (isVisible(flags, setterVisibility)) {
                        propName = decapitalize(name.substring(3));
                        isSetter = true;
                    }
                }
                // 5. Fluent setters — single-param methods returning the declaring type
                // (matches Jackson's INFER_BUILDER_PATTERN_AS_SETTERS, enabled by default since 2.18)
                if (!isGetter && !isSetter && method.parametersCount() == 1
                        && method.returnType().name().equals(classInfo.name())) {
                    if (isVisible(flags, setterVisibility)) {
                        propName = name;
                        isSetter = true;
                    }
                }
            }

            // Register in the appropriate map
            if (isGetter && propName != null) {
                getterMap.putIfAbsent(propName, name);
                // Check for @JsonProperty name override on the getter
                var jsonProp = method.annotation(JSON_PROPERTY);
                if (jsonProp != null) {
                    String explicitName = getAnnotationStringValue(jsonProp);
                    if (explicitName != null) {
                        jsonPropertyNames.putIfAbsent(propName, explicitName);
                    }
                }
            }
            if (isSetter && propName != null) {
                setterMap.putIfAbsent(propName,
                        new String[] { name, method.parameterType(0).name().toString() });
            }
        }
    }

    /**
     * Derives property name from a getter method using standard JavaBean naming.
     * Returns null if the method doesn't follow getter conventions.
     */
    private static String deriveGetterPropertyName(String methodName, MethodInfo method) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            String returnTypeName = method.returnType().name().toString();
            if ("boolean".equals(returnTypeName) || "java.lang.Boolean".equals(returnTypeName)) {
                return decapitalize(methodName.substring(2));
            }
        }
        return null;
    }

    /**
     * Derives property name from a setter method using standard JavaBean naming.
     * Returns null if the method doesn't follow setter conventions.
     */
    private static String deriveSetterPropertyName(String methodName) {
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        return null;
    }

    /**
     * Derives a property name from a method for @JsonIgnore purposes.
     */
    private static String derivePropertyNameFromMethod(MethodInfo method) {
        String name = method.name();
        if (method.parametersCount() == 0 && method.returnType().kind() != Type.Kind.VOID) {
            String propName = deriveGetterPropertyName(name, method);
            if (propName != null) {
                return propName;
            }
        }
        if (method.parametersCount() == 1) {
            String propName = deriveSetterPropertyName(name);
            if (propName != null) {
                return propName;
            }
        }
        return null;
    }

    /**
     * Extracts {@code @JsonProperty} name overrides from constructor/factory method parameters.
     * In standard Jackson, {@code @JsonProperty} on a constructor parameter renames the
     * corresponding property for both serialization and deserialization. This replicates that
     * by adding the explicit names to the property name map.
     */
    private static void extractCtorJsonPropertyNames(MethodInfo method, Map<String, String> jsonPropertyNames) {
        for (AnnotationInstance ann : method.annotations()) {
            if (ann.name().equals(JSON_PROPERTY)
                    && ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                AnnotationValue value = ann.value();
                if (value != null && !value.asString().isEmpty()) {
                    int pos = ann.target().asMethodParameter().position();
                    String paramName = method.parameterName(pos);
                    jsonPropertyNames.putIfAbsent(paramName, value.asString());
                }
            }
        }
    }

    /**
     * Extracts the string value from a Jackson annotation's "value" attribute.
     * Returns null if the value is absent or empty.
     */
    private static String getAnnotationStringValue(AnnotationInstance ann) {
        if (ann == null) {
            return null;
        }
        AnnotationValue value = ann.value();
        if (value == null) {
            return null;
        }
        String str = value.asString();
        return (str != null && !str.isEmpty()) ? str : null;
    }

    /**
     * Checks if a member with the given Java modifiers is visible according to the
     * specified {@link JsonAutoDetect.Visibility} level.
     */
    private static boolean isVisible(int modifiers, JsonAutoDetect.Visibility visibility) {
        switch (visibility) {
            case ANY:
                return true;
            case NON_PRIVATE:
                return !Modifier.isPrivate(modifiers);
            case PROTECTED_AND_PUBLIC:
                return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
            case PUBLIC_ONLY:
                return Modifier.isPublic(modifiers);
            case NONE:
                return false;
            default:
                return Modifier.isPublic(modifiers);
        }
    }

    /**
     * Standard JavaBean decapitalization: if the first two characters are uppercase,
     * the name is returned as-is (e.g. "URL" stays "URL"). Otherwise the first
     * character is lowercased.
     */
    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Checks whether any field or method in the class hierarchy has one of the given annotations.
     */
    private static boolean hasAnnotationInHierarchy(ClassInfo classInfo, IndexView index, DotName... annotationNames) {
        ClassInfo current = classInfo;
        while (current != null && !OBJECT.equals(current.name())) {
            for (FieldInfo field : current.fields()) {
                for (DotName ann : annotationNames) {
                    if (field.hasAnnotation(ann)) {
                        return true;
                    }
                }
            }
            for (MethodInfo method : current.methods()) {
                for (DotName ann : annotationNames) {
                    if (method.hasAnnotation(ann)) {
                        return true;
                    }
                }
            }
            current = current.superName() != null ? index.getClassByName(current.superName()) : null;
        }
        return false;
    }

    /**
     * Checks whether a class has a no-arg constructor.
     */
    private static boolean hasDefaultConstructor(ClassInfo classInfo) {
        for (MethodInfo method : classInfo.methods()) {
            if ("<init>".equals(method.name()) && method.parametersCount() == 0) {
                return true;
            }
        }
        return false;
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    public void clearCachesOnShutdown(JacksonRecorder recorder, ShutdownContextBuildItem shutdown) {
        recorder.clearCachesOnShutdown(shutdown);
    }

    private Optional<String> determinePropertyNamingStrategyClassName(JacksonBuildTimeConfig jacksonBuildTimeConfig) {
        if (jacksonBuildTimeConfig.propertyNamingStrategy().isEmpty()) {
            return Optional.empty();
        }
        var propertyNamingStrategy = jacksonBuildTimeConfig.propertyNamingStrategy().get();
        Field field;

        try {
            // let's first try and see if the value is a constant defined in PropertyNamingStrategies
            field = Reflections.findField(PropertyNamingStrategies.class, propertyNamingStrategy);
        } catch (Exception e) {
            // the provided value does not correspond to any of the defined constants, so let's see if it's actually a class name
            try {
                var clazz = Thread.currentThread().getContextClassLoader().loadClass(propertyNamingStrategy);
                if (PropertyNamingStrategy.class.isAssignableFrom(clazz)) {
                    return Optional.of(propertyNamingStrategy);

                }
                throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
            }
        }

        try {
            // we have a matching field, so let's see if the type is correct
            Class<?> clazz = field.get(null).getClass();
            if (PropertyNamingStrategy.class.isAssignableFrom(clazz)) {
                return Optional.of(clazz.getName());
            }
            throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
        } catch (IllegalAccessException e) {
            // shouldn't ever happen
            throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
        }
    }

    private static String invalidPropertyNameStrategyValueMessage(String propertyNamingStrategy) {
        return "Unable to determine the property naming strategy for value '" + propertyNamingStrategy
                + "'. Make sure that the value is either a fully qualified class name of a subclass of '"
                + PropertyNamingStrategy.class.getName()
                + "' or one of the constants defined in '" + PropertyNamingStrategies.class.getName() + "'.";
    }
}
