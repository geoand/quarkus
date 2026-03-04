package io.quarkus.bean.validator.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.valueextraction.ValueExtractor;
import jakarta.ws.rs.Priorities;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassVisitor;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.bean.validation.impl.constraints.BuiltinConstraintValidators;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedBeanMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedFieldMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedMethodMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedParameterMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstraintMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ContainerElementConstraint;
import io.quarkus.bean.validation.processor.BeanValidationProcessor;
import io.quarkus.bean.validator.runtime.BeanValidatorRecorder;
import io.quarkus.bean.validator.runtime.interceptor.MethodValidationInterceptor;
import io.quarkus.bean.validator.runtime.jaxrs.ResteasyReactiveViolationExceptionMapper;
import io.quarkus.bean.validator.runtime.jaxrs.ViolationReport;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.GeneratedClassGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ConfigClassBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.StaticInitConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBundleBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.resteasy.common.spi.ResteasyDotNames;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;

class BeanValidatorProcessor {

    static final class BeanValidationMetadataBuildItem extends SimpleBuildItem {
        private final BeanValidationMetadata metadata;

        BeanValidationMetadataBuildItem(BeanValidationMetadata metadata) {
            this.metadata = metadata;
        }

        BeanValidationMetadata getMetadata() {
            return metadata;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BeanValidatorProcessor.class);

    private static final String FEATURE = "bean-validator";

    private static final DotName CONSTRAINT_VALIDATOR = DotName.createSimple(ConstraintValidator.class);
    private static final DotName VALUE_EXTRACTOR = DotName.createSimple(ValueExtractor.class);
    private static final DotName CONSTRAINT = DotName.createSimple(Constraint.class);
    private static final DotName VALID = DotName.createSimple(Valid.class);
    private static final Pattern REPEATABLE_CONTAINER_PATTERN = Pattern.compile("\\$List$");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerMessageBundles(BuildProducer<NativeImageResourceBundleBuildItem> resourceBundles) {
        resourceBundles.produce(new NativeImageResourceBundleBuildItem("ValidationMessages"));
        resourceBundles
                .produce(new NativeImageResourceBundleBuildItem("io.quarkus.bean.validation.DefaultValidationMessages"));
    }

    @BuildStep
    IndexDependencyBuildItem indexJakartaValidationApi() {
        return new IndexDependencyBuildItem("jakarta.validation", "jakarta.validation-api");
    }

    @BuildStep
    BeanValidationMetadataBuildItem processMetadata(CombinedIndexBuildItem combinedIndex) {
        BeanValidationProcessor processor = new BeanValidationProcessor();
        BeanValidationMetadata metadata = processor.process(combinedIndex.getIndex());
        return new BeanValidationMetadataBuildItem(metadata);
    }

    @BuildStep
    void registerConstraintValidatorsAndValueExtractors(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        // Register ConstraintValidator and ValueExtractor implementors as CDI beans
        for (DotName interfaceName : List.of(CONSTRAINT_VALIDATOR, VALUE_EXTRACTOR)) {
            for (ClassInfo classInfo : combinedIndex.getIndex().getAllKnownImplementations(interfaceName)) {
                if (Modifier.isAbstract(classInfo.flags()) || Modifier.isInterface(classInfo.flags())) {
                    continue;
                }
                additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(classInfo.name().toString()));
            }
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    void createBeans(
            RecorderContext recorderContext,
            BeanValidatorRecorder recorder,
            CombinedIndexBuildItem combinedIndex,
            BeanValidationMetadataBuildItem metadataBuildItem,
            io.quarkus.runtime.LocalesBuildTimeConfig localesBuildTimeConfig,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedResourceBuildItem> generatedResources) throws Exception {

        // Register metadata POJOs for bytecode recording
        registerMetadataTypes(recorderContext);

        BeanValidationMetadata metadata = metadataBuildItem.getMetadata();

        // Register constrained beans and their validators for reflection (native image)
        registerForReflection(metadata, reflectiveClasses);

        // Generate annotation literal classes
        Map<String, String> annotationToLiteral = generateAnnotationLiterals(
                metadata, combinedIndex, generatedClasses, generatedResources, reflectiveClasses);

        // Generate property accessor classes for constrained beans
        Map<String, String> beanToAccessor = generatePropertyAccessors(
                metadata, combinedIndex, generatedClasses, generatedResources, reflectiveClasses);

        // Generate reflection-free validator instantiator
        String validatorInstantiatorClassName = generateValidatorInstantiator(
                metadata, combinedIndex, generatedClasses, generatedResources, reflectiveClasses);

        // Collect custom value extractor class names
        List<String> valueExtractorClassNames = new ArrayList<>();
        for (ClassInfo classInfo : combinedIndex.getIndex().getAllKnownImplementations(VALUE_EXTRACTOR)) {
            if (Modifier.isAbstract(classInfo.flags()) || Modifier.isInterface(classInfo.flags())) {
                continue;
            }
            valueExtractorClassNames.add(classInfo.name().toString());
        }

        // Synthetic bean: ValidatorFactory (singleton, unremovable)
        syntheticBeans.produce(SyntheticBeanBuildItem
                .configure(ValidatorFactory.class)
                .unremovable()
                .scope(BuiltinScope.SINGLETON.getInfo())
                .createWith(recorder.createValidatorFactory(metadata,
                        valueExtractorClassNames.isEmpty() ? null : valueExtractorClassNames,
                        localesBuildTimeConfig,
                        annotationToLiteral, beanToAccessor, validatorInstantiatorClassName))
                .done());

        // Synthetic bean: Validator (singleton, unremovable, depends on ValidatorFactory)
        syntheticBeans.produce(SyntheticBeanBuildItem
                .configure(Validator.class)
                .unremovable()
                .scope(BuiltinScope.SINGLETON.getInfo())
                .addInjectionPoint(ClassType.create(DotName.createSimple(ValidatorFactory.class)))
                .createWith(recorder.createValidator())
                .done());
    }

    @BuildStep
    void methodValidation(
            CombinedIndexBuildItem combinedIndex,
            Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformers) {

        // Register the method validation interceptor
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(MethodValidationInterceptor.class));

        if (capabilities.isPresent(Capability.RESTEASY_REACTIVE)) {
            // Register the JAX-RS endpoint validation interceptor for RESTEasy Reactive
            additionalBeans.produce(new AdditionalBeanBuildItem(
                    "io.quarkus.bean.validator.runtime.jaxrs.ResteasyReactiveEndPointValidationInterceptor"));
        }

        // Collect all constraint annotations from the index
        Set<DotName> consideredAnnotations = collectConstraintAnnotations(combinedIndex.getIndex());
        consideredAnnotations.add(VALID);

        // Gather JAX-RS methods so the transformer can distinguish REST endpoints
        Map<DotName, Set<SimpleMethodSignatureKey>> jaxRsMethods = gatherJaxRsMethods(
                combinedIndex.getIndex(), capabilities);

        // Add annotations transformer that applies @JaxrsEndPointValidated or @MethodValidated
        annotationsTransformers.produce(new AnnotationsTransformerBuildItem(
                (AnnotationTransformation) new MethodValidatedAnnotationsTransformer(consideredAnnotations, jaxRsMethods)));
    }

    @BuildStep
    void exceptionMapper(Capabilities capabilities,
            BuildProducer<ExceptionMapperBuildItem> exceptionMapperProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        if (capabilities.isPresent(Capability.RESTEASY_REACTIVE)) {
            exceptionMapperProducer.produce(new ExceptionMapperBuildItem(
                    ResteasyReactiveViolationExceptionMapper.class.getName(),
                    ValidationException.class.getName(), Priorities.USER + 1, true));
            reflectiveClassProducer.produce(
                    ReflectiveClassBuildItem.builder(ViolationReport.class, ViolationReport.Violation.class)
                            .reason(getClass().getName())
                            .methods().fields().build());
        }
    }

    /**
     * Removes the {@code private} modifier from constrained fields and getter methods
     * so that the generated {@link io.quarkus.bean.validation.BeanPropertyAccessor} can
     * access them directly without {@code setAccessible(true)}.
     */
    @BuildStep
    void removePrivateModifiers(
            CombinedIndexBuildItem combinedIndex,
            BeanValidationMetadataBuildItem metadataBuildItem,
            BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformers) {

        IndexView index = combinedIndex.getIndex();
        BeanValidationMetadata metadata = metadataBuildItem.getMetadata();

        // Collect private fields and getters grouped by declaring class
        Map<String, List<FieldDescriptor>> privateFieldsByClass = new HashMap<>();
        Map<String, List<MethodDescriptor>> privateGettersByClass = new HashMap<>();

        for (ConstrainedBeanMetadata bean : metadata.beans().values()) {
            for (ConstrainedFieldMetadata field : bean.fields()) {
                ClassInfo classInfo = index.getClassByName(DotName.createSimple(field.declaringClassName()));
                if (classInfo == null) {
                    continue;
                }
                FieldInfo fieldInfo = classInfo.field(field.name());
                if (fieldInfo != null && Modifier.isPrivate(fieldInfo.flags())) {
                    privateFieldsByClass
                            .computeIfAbsent(field.declaringClassName(), k -> new ArrayList<>())
                            .add(FieldDescriptor.of(fieldInfo));
                }
            }

            for (ConstrainedMethodMetadata method : bean.methods()) {
                if (!method.getter()) {
                    continue;
                }
                ClassInfo classInfo = index.getClassByName(DotName.createSimple(method.declaringClassName()));
                if (classInfo == null) {
                    continue;
                }
                for (MethodInfo mi : classInfo.methods()) {
                    if (mi.name().equals(method.name()) && mi.parametersCount() == 0
                            && Modifier.isPrivate(mi.flags())) {
                        privateGettersByClass
                                .computeIfAbsent(method.declaringClassName(), k -> new ArrayList<>())
                                .add(MethodDescriptor.of(mi));
                        break;
                    }
                }
            }
        }

        // Produce one BytecodeTransformerBuildItem per declaring class that has private members
        Set<String> allClasses = new HashSet<>();
        allClasses.addAll(privateFieldsByClass.keySet());
        allClasses.addAll(privateGettersByClass.keySet());

        for (String className : allClasses) {
            List<FieldDescriptor> fields = privateFieldsByClass.getOrDefault(className, List.of());
            List<MethodDescriptor> getters = privateGettersByClass.getOrDefault(className, List.of());

            bytecodeTransformers.produce(new BytecodeTransformerBuildItem(className,
                    new BiFunction<String, ClassVisitor, ClassVisitor>() {
                        @Override
                        public ClassVisitor apply(String cls, ClassVisitor classVisitor) {
                            ClassTransformer classTransformer = new ClassTransformer(cls);
                            for (FieldDescriptor fd : fields) {
                                classTransformer.modifyField(fd).removeModifiers(Modifier.PRIVATE);
                            }
                            for (MethodDescriptor md : getters) {
                                classTransformer.modifyMethod(md).removeModifiers(Modifier.PRIVATE);
                            }
                            return classTransformer.applyTo(classVisitor);
                        }
                    }));
        }
    }

    private Set<DotName> collectConstraintAnnotations(IndexView index) {
        Set<DotName> annotations = new HashSet<>();

        // Add built-in Jakarta BV constraints (not in the application index)
        // Also add the $List repeatable container annotation for each built-in constraint,
        // since repeatable annotations are stored as their container in bytecode
        for (String builtinName : BuiltinConstraintValidators.getAllConstraintAnnotationNames()) {
            annotations.add(DotName.createSimple(builtinName));
            annotations.add(DotName.createSimple(builtinName + "$List"));
        }

        // Add custom constraint annotations found in the index
        for (AnnotationInstance constraint : index.getAnnotations(CONSTRAINT)) {
            if (constraint.target().kind() == AnnotationTarget.Kind.CLASS) {
                DotName constraintAnnotation = constraint.target().asClass().name();
                annotations.add(constraintAnnotation);

                // Also check for @Repeatable container
                ClassInfo constraintClass = index.getClassByName(constraintAnnotation);
                if (constraintClass != null) {
                    AnnotationInstance repeatable = constraintClass
                            .annotation(DotName.createSimple("java.lang.annotation.Repeatable"));
                    if (repeatable != null) {
                        annotations.add(repeatable.value().asClass().name());
                    }
                }
            }
        }
        return annotations;
    }

    private static Map<DotName, Set<SimpleMethodSignatureKey>> gatherJaxRsMethods(
            IndexView indexView, Capabilities capabilities) {
        if (!capabilities.isPresent(Capability.RESTEASY_REACTIVE) && !capabilities.isPresent(Capability.RESTEASY)) {
            return Map.of();
        }

        Map<DotName, Set<SimpleMethodSignatureKey>> jaxRsMethods = new HashMap<>();

        for (DotName jaxRsAnnotation : ResteasyDotNames.JAXRS_METHOD_ANNOTATIONS) {
            Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(jaxRsAnnotation);

            if (annotationInstances.isEmpty()) {
                continue;
            }

            for (AnnotationInstance annotation : annotationInstances) {
                if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                    MethodInfo method = annotation.target().asMethod();
                    jaxRsMethods.computeIfAbsent(method.declaringClass().name(), k -> new HashSet<>())
                            .add(new SimpleMethodSignatureKey(method));

                    if (Modifier.isInterface(method.declaringClass().flags())) {
                        for (ClassInfo implementor : indexView.getAllKnownImplementors(method.declaringClass().name())) {
                            jaxRsMethods.computeIfAbsent(implementor.name(), k -> new HashSet<>())
                                    .add(new SimpleMethodSignatureKey(method));
                        }
                    } else {
                        for (ClassInfo subclass : indexView.getAllKnownSubclasses(method.declaringClass().name())) {
                            jaxRsMethods.computeIfAbsent(subclass.name(), k -> new HashSet<>())
                                    .add(new SimpleMethodSignatureKey(method));
                        }
                    }
                }
            }
        }
        return jaxRsMethods;
    }

    @BuildStep
    void configValidator(
            CombinedIndexBuildItem combinedIndex,
            BeanValidationMetadataBuildItem metadataBuildItem,
            List<ConfigClassBuildItem> configClasses,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<GeneratedResourceBuildItem> generatedResource,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<StaticInitConfigBuilderBuildItem> staticInitConfigBuilder,
            BuildProducer<RunTimeConfigBuilderBuildItem> runTimeConfigBuilder) {

        // Collect config mappings and their generated classes
        Set<DotName> configMappings = new HashSet<>();
        Set<DotName> configClassesToValidate = new HashSet<>();
        Map<DotName, Map<DotName, ConfigClassBuildItem>> embeddingMap = new HashMap<>();
        for (ConfigClassBuildItem configClass : configClasses) {
            for (String generatedConfigClass : configClass.getGeneratedClasses()) {
                DotName simple = DotName.createSimple(generatedConfigClass);
                configClassesToValidate.add(simple);
            }

            configClass.getConfigComponentInterfaces().stream().map(DotName::createSimple)
                    .forEach(cm -> {
                        configMappings.add(cm);
                        embeddingMap.computeIfAbsent(cm, c -> new HashMap<>())
                                .putIfAbsent(configClass.getName(), configClass);
                    });
        }

        // Collect constraint annotations used on config mappings
        Set<DotName> consideredAnnotations = collectConstraintAnnotations(combinedIndex.getIndex());
        consideredAnnotations.add(VALID);

        Set<DotName> constrainedConfigMappings = new HashSet<>();
        Set<String> configMappingsConstraints = new HashSet<>();

        for (DotName consideredAnnotation : consideredAnnotations) {
            Collection<AnnotationInstance> annotationInstances = combinedIndex.getIndex()
                    .getAnnotations(consideredAnnotation);

            if (annotationInstances.isEmpty()) {
                continue;
            }

            for (AnnotationInstance annotation : annotationInstances) {
                String builtinConstraintCandidate = REPEATABLE_CONTAINER_PATTERN
                        .matcher(consideredAnnotation.toString()).replaceAll("");

                if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                    MethodInfo methodInfo = annotation.target().asMethod();
                    ClassInfo declaringClass = methodInfo.declaringClass();
                    if (configMappings.contains(declaringClass.name())) {
                        configMappingsConstraints.add(builtinConstraintCandidate);
                        constrainedConfigMappings.add(declaringClass.name());
                    }
                } else if (annotation.target().kind() == AnnotationTarget.Kind.TYPE) {
                    AnnotationTarget target = annotation.target().asType().enclosingTarget();
                    if (target.kind() == AnnotationTarget.Kind.METHOD) {
                        MethodInfo methodInfo = target.asMethod();
                        ClassInfo declaringClass = methodInfo.declaringClass();
                        if (configMappings.contains(declaringClass.name())) {
                            configMappingsConstraints.add(builtinConstraintCandidate);
                            constrainedConfigMappings.add(declaringClass.name());
                        }
                    }
                } else if (annotation.target().kind() == AnnotationTarget.Kind.CLASS) {
                    ClassInfo classInfo = annotation.target().asClass();
                    if (configMappings.contains(classInfo.name())) {
                        configMappingsConstraints.add(builtinConstraintCandidate);
                        constrainedConfigMappings.add(classInfo.name());
                    }
                }
            }
        }

        if (configMappingsConstraints.isEmpty()) {
            LOGGER.debugf("No config mapping constraints found, skipping config validator generation");
            return;
        }

        LOGGER.infof("Found config mapping constraints: %s on mappings: %s", configMappingsConstraints,
                constrainedConfigMappings);

        // Register the whole tree of constrained config mappings for reflection
        Set<DotName> configComponentsInterfacesToRegisterForReflection = new HashSet<>();
        for (DotName constrainedConfigMapping : constrainedConfigMappings) {
            if (!embeddingMap.containsKey(constrainedConfigMapping)) {
                continue;
            }

            for (ConfigClassBuildItem configClass : embeddingMap.get(constrainedConfigMapping).values()) {
                unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(configClass.getConfigClass()));
                configClass.getConfigComponentInterfaces()
                        .stream()
                        .map(DotName::createSimple)
                        .forEach(configComponentsInterfacesToRegisterForReflection::add);
            }
        }
        reflectiveClass.produce(ReflectiveClassBuildItem
                .builder(configComponentsInterfacesToRegisterForReflection.stream().map(DotName::toString)
                        .toArray(String[]::new))
                .reason(getClass().getName())
                .methods().build());

        // Build config validation metadata at build time using Jandex (no runtime reflection)
        BeanValidationMetadata configMetadata = buildConfigValidationMetadata(
                metadataBuildItem.getMetadata(), constrainedConfigMappings, configMappingsConstraints);

        // Generate ConfigBuilder class via Gizmo that constructs metadata from build-time constants
        String builderClassName = io.quarkus.bean.validator.runtime.QuarkusBeanValidationConfigValidator.class.getName()
                + "Builder";
        io.quarkus.gizmo2.Gizmo gizmo = io.quarkus.gizmo2.Gizmo
                .create(new GeneratedClassGizmo2Adaptor(generatedClass, generatedResource, true))
                .withDebugInfo(false)
                .withParameters(false);
        gizmo.class_(builderClassName, cc -> {
            cc.final_();
            cc.implements_(io.quarkus.runtime.configuration.ConfigBuilder.class);

            io.quarkus.gizmo2.StaticFieldVar configValidator = cc.staticField("configValidator", fc -> {
                fc.private_();
                fc.final_();
                fc.setType(io.smallrye.config.validator.BeanValidationConfigValidator.class);
                fc.setInitializer(bc -> {
                    int[] c = { 0 }; // counter for unique variable names

                    // Construct BeanValidationMetadata from build-time constants
                    Expr metadataExpr = emitBeanValidationMetadata(bc, configMetadata, c);

                    // Construct Set<Class<?>> for config classes to validate
                    LocalVar classes = bc.localVar("classes", bc.new_(HashSet.class));
                    for (DotName configClassToValidate : configClassesToValidate) {
                        bc.withSet(classes).add(Const.of(
                                org.jboss.jandex.gizmo2.Jandex2Gizmo.classDescOf(configClassToValidate)));
                    }

                    bc.yield(bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(
                            io.quarkus.bean.validator.runtime.QuarkusBeanValidationConfigValidator.class,
                            BeanValidationMetadata.class, Set.class),
                            metadataExpr, classes));
                });
            });

            cc.defaultConstructor();

            cc.method("configBuilder", mc -> {
                mc.returning(io.smallrye.config.SmallRyeConfigBuilder.class);
                io.quarkus.gizmo2.ParamVar builder = mc.parameter("builder",
                        io.smallrye.config.SmallRyeConfigBuilder.class);
                mc.body(bc -> {
                    io.quarkus.gizmo2.desc.MethodDesc withValidator = io.quarkus.gizmo2.desc.MethodDesc.of(
                            io.smallrye.config.SmallRyeConfigBuilder.class, "withValidator",
                            io.smallrye.config.SmallRyeConfigBuilder.class,
                            io.smallrye.config.ConfigValidator.class);

                    bc.invokeVirtual(withValidator, builder, configValidator);
                    bc.return_(builder);
                });
            });
        });

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(builderClassName).build());
        staticInitConfigBuilder.produce(new StaticInitConfigBuilderBuildItem(builderClassName));
        runTimeConfigBuilder.produce(new RunTimeConfigBuilderBuildItem(builderClassName));
    }

    private BeanValidationMetadata buildConfigValidationMetadata(
            BeanValidationMetadata fullMetadata,
            Set<DotName> constrainedConfigMappings,
            Set<String> configMappingsConstraints) {

        // Extract only the config mapping beans
        Map<String, ConstrainedBeanMetadata> configBeans = new HashMap<>();
        for (DotName constrainedMapping : constrainedConfigMappings) {
            String name = constrainedMapping.toString();
            ConstrainedBeanMetadata bean = fullMetadata.getBean(name);
            if (bean != null) {
                configBeans.put(name, bean);
            }
        }

        // Extract constraint-validator mapping for the constraints used on config mappings
        Map<String, java.util.List<String>> cvMapping = new HashMap<>();
        for (String constraintName : configMappingsConstraints) {
            java.util.List<String> validators = fullMetadata.constraintValidatorMapping().get(constraintName);
            if (validators != null) {
                cvMapping.put(constraintName, validators);
            }
        }

        return new BeanValidationMetadata(configBeans,
                new java.util.HashSet<>(), new java.util.HashSet<>(), new java.util.HashMap<>(),
                new java.util.HashSet<>(), new java.util.HashMap<>(), cvMapping,
                new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>(),
                new java.util.HashMap<>());
    }

    // ---- Gizmo metadata emission helpers ----

    private Expr emitBeanValidationMetadata(BlockCreator bc, BeanValidationMetadata metadata, int[] c) {
        // beans map
        LocalVar beans = bc.localVar("beans" + c[0]++, bc.new_(HashMap.class));
        for (Map.Entry<String, ConstrainedBeanMetadata> entry : metadata.beans().entrySet()) {
            Expr beanExpr = emitConstrainedBeanMetadata(bc, entry.getValue(), c);
            bc.withMap(beans).put(Const.of(entry.getKey()), beanExpr);
        }

        // constraint-validator mapping
        LocalVar cvMapping = bc.localVar("cvm" + c[0]++, bc.new_(HashMap.class));
        for (Map.Entry<String, List<String>> entry : metadata.constraintValidatorMapping().entrySet()) {
            LocalVar validators = bc.localVar("cvl" + c[0]++, bc.new_(ArrayList.class));
            for (String validator : entry.getValue()) {
                bc.withList(validators).add(Const.of(validator));
            }
            bc.withMap(cvMapping).put(Const.of(entry.getKey()), validators);
        }

        // iterableTypeArguments map
        LocalVar iterableTypeArgs = bc.localVar("ita" + c[0]++, bc.new_(HashMap.class));
        for (Map.Entry<String, String> entry : metadata.iterableTypeArguments().entrySet()) {
            bc.withMap(iterableTypeArgs).put(Const.of(entry.getKey()), Const.of(entry.getValue()));
        }

        // 11-arg constructor
        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(BeanValidationMetadata.class,
                Map.class, Set.class, Set.class, Map.class, Set.class, Map.class,
                Map.class, Map.class, Map.class, Map.class, Map.class),
                beans,
                bc.new_(HashSet.class),
                bc.new_(HashSet.class),
                bc.new_(HashMap.class),
                bc.new_(HashSet.class),
                bc.new_(HashMap.class),
                cvMapping,
                bc.new_(HashMap.class),
                bc.new_(HashMap.class),
                bc.new_(HashMap.class),
                iterableTypeArgs);
    }

    private Expr emitConstrainedBeanMetadata(BlockCreator bc, ConstrainedBeanMetadata bean, int[] c) {
        // class constraints
        LocalVar classConstraints = bc.localVar("cc" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cm : bean.classConstraints()) {
            bc.withList(classConstraints).add(emitConstraintMetadata(bc, cm, c));
        }

        // fields
        LocalVar fields = bc.localVar("flds" + c[0]++, bc.new_(ArrayList.class));
        for (ConstrainedFieldMetadata field : bean.fields()) {
            bc.withList(fields).add(emitConstrainedFieldMetadata(bc, field, c));
        }

        // methods
        LocalVar methods = bc.localVar("mtds" + c[0]++, bc.new_(ArrayList.class));
        for (ConstrainedMethodMetadata method : bean.methods()) {
            bc.withList(methods).add(emitConstrainedMethodMetadata(bc, method, c));
        }

        // constructors
        LocalVar constructors = bc.localVar("ctors" + c[0]++, bc.new_(ArrayList.class));
        for (ConstrainedMethodMetadata ctor : bean.constructors()) {
            bc.withList(constructors).add(emitConstrainedMethodMetadata(bc, ctor, c));
        }

        // group sequence (nullable)
        Expr groupSequence;
        if (bean.groupSequence() != null) {
            LocalVar gs = bc.localVar("gs" + c[0]++, bc.new_(ArrayList.class));
            for (String group : bean.groupSequence()) {
                bc.withList(gs).add(Const.of(group));
            }
            groupSequence = gs;
        } else {
            groupSequence = Const.ofNull(List.class);
        }

        // type hierarchy (nullable)
        Expr typeHierarchy;
        if (bean.typeHierarchy() != null) {
            LocalVar th = bc.localVar("th" + c[0]++, bc.new_(ArrayList.class));
            for (String t : bean.typeHierarchy()) {
                bc.withList(th).add(Const.of(t));
            }
            typeHierarchy = th;
        } else {
            typeHierarchy = Const.ofNull(List.class);
        }

        // allPropertyNames (nullable)
        Expr allPropertyNames;
        if (bean.allPropertyNames() != null) {
            LocalVar apn = bc.localVar("apn" + c[0]++, bc.new_(java.util.LinkedHashSet.class));
            for (String name : bean.allPropertyNames()) {
                bc.withSet(apn).add(Const.of(name));
            }
            allPropertyNames = apn;
        } else {
            allPropertyNames = Const.ofNull(Set.class);
        }

        // 8-arg constructor
        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(ConstrainedBeanMetadata.class,
                String.class, List.class, List.class, List.class, List.class,
                List.class, List.class, Set.class),
                Const.of(bean.className()),
                classConstraints, fields, methods, constructors,
                groupSequence, typeHierarchy, allPropertyNames);
    }

    private Expr emitConstrainedFieldMetadata(BlockCreator bc, ConstrainedFieldMetadata field, int[] c) {
        LocalVar constraints = bc.localVar("fc" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cm : field.constraints()) {
            bc.withList(constraints).add(emitConstraintMetadata(bc, cm, c));
        }

        LocalVar containerElements = bc.localVar("fce" + c[0]++, bc.new_(ArrayList.class));
        for (ContainerElementConstraint cec : field.containerElementConstraints()) {
            bc.withList(containerElements).add(emitContainerElementConstraint(bc, cec, c));
        }

        Expr groupConversions = emitStringMap(bc, field.groupConversions(), c);

        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(ConstrainedFieldMetadata.class,
                String.class, String.class, String.class, List.class,
                boolean.class, List.class, Map.class),
                Const.of(field.name()),
                Const.of(field.declaringClassName()),
                Const.of(field.fieldTypeName()),
                constraints,
                Const.of(field.cascading()),
                containerElements,
                groupConversions);
    }

    private Expr emitConstrainedMethodMetadata(BlockCreator bc, ConstrainedMethodMetadata method, int[] c) {
        // parameter type names
        LocalVar paramTypeNames = bc.localVar("ptn" + c[0]++, bc.new_(ArrayList.class));
        for (String ptn : method.parameterTypeNames()) {
            bc.withList(paramTypeNames).add(Const.of(ptn));
        }

        // parameters
        LocalVar parameters = bc.localVar("params" + c[0]++, bc.new_(ArrayList.class));
        for (ConstrainedParameterMetadata param : method.parameters()) {
            bc.withList(parameters).add(emitConstrainedParameterMetadata(bc, param, c));
        }

        // return value constraints
        LocalVar rvConstraints = bc.localVar("rvc" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cm : method.returnValueConstraints()) {
            bc.withList(rvConstraints).add(emitConstraintMetadata(bc, cm, c));
        }

        // cross-parameter constraints
        LocalVar cpConstraints = bc.localVar("cpc" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cm : method.crossParameterConstraints()) {
            bc.withList(cpConstraints).add(emitConstraintMetadata(bc, cm, c));
        }

        // return value container element constraints
        LocalVar rvContainerElements = bc.localVar("rvce" + c[0]++, bc.new_(ArrayList.class));
        for (ContainerElementConstraint cec : method.returnValueContainerElementConstraints()) {
            bc.withList(rvContainerElements).add(emitContainerElementConstraint(bc, cec, c));
        }

        Expr rvGroupConversions = emitStringMap(bc, method.returnValueGroupConversions(), c);

        // 12-arg constructor
        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(ConstrainedMethodMetadata.class,
                String.class, String.class, String.class, List.class,
                List.class, List.class, boolean.class, List.class,
                List.class, Map.class, boolean.class, boolean.class),
                Const.of(method.name()),
                Const.of(method.declaringClassName()),
                Const.of(method.returnTypeName()),
                paramTypeNames, parameters, rvConstraints,
                Const.of(method.returnValueCascading()),
                cpConstraints, rvContainerElements, rvGroupConversions,
                Const.of(method.constructor()),
                Const.of(method.getter()));
    }

    private Expr emitConstrainedParameterMetadata(BlockCreator bc, ConstrainedParameterMetadata param, int[] c) {
        LocalVar constraints = bc.localVar("pc" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cm : param.constraints()) {
            bc.withList(constraints).add(emitConstraintMetadata(bc, cm, c));
        }

        LocalVar containerElements = bc.localVar("pce" + c[0]++, bc.new_(ArrayList.class));
        for (ContainerElementConstraint cec : param.containerElementConstraints()) {
            bc.withList(containerElements).add(emitContainerElementConstraint(bc, cec, c));
        }

        Expr groupConversions = emitStringMap(bc, param.groupConversions(), c);

        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(ConstrainedParameterMetadata.class,
                String.class, int.class, String.class, List.class,
                boolean.class, List.class, Map.class),
                Const.of(param.name()),
                Const.of(param.index()),
                Const.of(param.typeName()),
                constraints,
                Const.of(param.cascading()),
                containerElements,
                groupConversions);
    }

    private Expr emitConstraintMetadata(BlockCreator bc, ConstraintMetadata cm, int[] c) {
        // attributes map
        LocalVar attrs = bc.localVar("a" + c[0]++, bc.new_(java.util.LinkedHashMap.class));
        for (Map.Entry<String, Object> entry : cm.attributes().entrySet()) {
            bc.withMap(attrs).put(Const.of(entry.getKey()), emitAttributeValue(bc, entry.getValue(), c));
        }

        // groups
        LocalVar groups = bc.localVar("g" + c[0]++, bc.new_(ArrayList.class));
        for (String group : cm.groups()) {
            bc.withList(groups).add(Const.of(group));
        }

        // payload
        LocalVar payload = bc.localVar("p" + c[0]++, bc.new_(ArrayList.class));
        for (String pl : cm.payload()) {
            bc.withList(payload).add(Const.of(pl));
        }

        // composing constraints
        LocalVar composing = bc.localVar("comp" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cc : cm.composingConstraints()) {
            bc.withList(composing).add(emitConstraintMetadata(bc, cc, c));
        }

        // overrides attributes
        LocalVar overrides = bc.localVar("ov" + c[0]++, bc.new_(java.util.LinkedHashMap.class));
        for (Map.Entry<String, Map<String, String>> entry : cm.overridesAttributes().entrySet()) {
            Expr innerMap = emitStringMap(bc, entry.getValue(), c);
            bc.withMap(overrides).put(Const.of(entry.getKey()), innerMap);
        }

        // all validator class names
        LocalVar allValidators = bc.localVar("av" + c[0]++, bc.new_(ArrayList.class));
        for (String v : cm.allValidatorClassNames()) {
            bc.withList(allValidators).add(Const.of(v));
        }

        Expr annotationClassName = cm.annotationClassName() != null
                ? Const.of(cm.annotationClassName())
                : Const.ofNull(String.class);
        Expr validatorClassName = cm.validatorClassName() != null
                ? Const.of(cm.validatorClassName())
                : Const.ofNull(String.class);
        Expr defaultMessageTemplate = cm.defaultMessageTemplate() != null
                ? Const.of(cm.defaultMessageTemplate())
                : Const.ofNull(String.class);

        // attribute types
        LocalVar attrTypes = bc.localVar("at" + c[0]++, bc.new_(java.util.LinkedHashMap.class));
        for (Map.Entry<String, String> entry : cm.attributeTypes().entrySet()) {
            bc.withMap(attrTypes).put(Const.of(entry.getKey()), Const.of(entry.getValue()));
        }

        // 11-arg constructor
        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(ConstraintMetadata.class,
                String.class, Map.class, List.class, List.class, String.class,
                boolean.class, List.class, Map.class, String.class, List.class, Map.class),
                annotationClassName, attrs, groups, payload, validatorClassName,
                Const.of(cm.reportAsSingleViolation()),
                composing, overrides, defaultMessageTemplate, allValidators, attrTypes);
    }

    private Expr emitContainerElementConstraint(BlockCreator bc, ContainerElementConstraint cec, int[] c) {
        LocalVar constraints = bc.localVar("cec" + c[0]++, bc.new_(ArrayList.class));
        for (ConstraintMetadata cm : cec.constraints()) {
            bc.withList(constraints).add(emitConstraintMetadata(bc, cm, c));
        }

        Expr groupConversions = emitStringMap(bc, cec.groupConversions(), c);

        LocalVar nested = bc.localVar("ncec" + c[0]++, bc.new_(ArrayList.class));
        for (ContainerElementConstraint nestedCec : cec.nestedContainerElements()) {
            bc.withList(nested).add(emitContainerElementConstraint(bc, nestedCec, c));
        }

        Expr containerClassName = cec.containerClassName() != null
                ? Const.of(cec.containerClassName())
                : Const.ofNull(String.class);

        // 6-arg constructor
        return bc.new_(io.quarkus.gizmo2.desc.ConstructorDesc.of(ContainerElementConstraint.class,
                int.class, List.class, boolean.class, String.class, Map.class, List.class),
                Const.of(cec.typeArgumentIndex()),
                constraints,
                Const.of(cec.cascading()),
                containerClassName,
                groupConversions,
                nested);
    }

    private Expr emitStringMap(BlockCreator bc, Map<String, String> map, int[] c) {
        LocalVar mapVar = bc.localVar("sm" + c[0]++, bc.new_(java.util.LinkedHashMap.class));
        for (Map.Entry<String, String> entry : map.entrySet()) {
            bc.withMap(mapVar).put(Const.of(entry.getKey()), Const.of(entry.getValue()));
        }
        return mapVar;
    }

    /**
     * Emits a Gizmo expression for an annotation attribute value.
     * Handles String, boxed primitives, List, and Map types.
     */
    @SuppressWarnings("unchecked")
    private Expr emitAttributeValue(BlockCreator bc, Object value, int[] c) {
        if (value == null) {
            return Const.ofNull(Object.class);
        }
        if (value instanceof String s) {
            return Const.of(s);
        }
        if (value instanceof Boolean b) {
            return bc.box(Const.of((boolean) b));
        }
        if (value instanceof Integer i) {
            return bc.box(Const.of((int) i));
        }
        if (value instanceof Long l) {
            return bc.box(Const.of((long) l));
        }
        if (value instanceof Float f) {
            return bc.box(Const.of((float) f));
        }
        if (value instanceof Double d) {
            return bc.box(Const.of((double) d));
        }
        if (value instanceof Character ch) {
            return bc.box(Const.of((int) (char) ch));
        }
        if (value instanceof Byte b) {
            return bc.box(Const.of((int) (byte) b));
        }
        if (value instanceof Short s) {
            return bc.box(Const.of((int) (short) s));
        }
        if (value instanceof List<?> list) {
            LocalVar listVar = bc.localVar("al" + c[0]++, bc.new_(ArrayList.class));
            for (Object item : list) {
                bc.withList(listVar).add(emitAttributeValue(bc, item, c));
            }
            return listVar;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            LocalVar mapVar = bc.localVar("am" + c[0]++, bc.new_(java.util.LinkedHashMap.class));
            for (Map.Entry<String, Object> entry : typedMap.entrySet()) {
                bc.withMap(mapVar).put(Const.of(entry.getKey()), emitAttributeValue(bc, entry.getValue(), c));
            }
            return mapVar;
        }
        // Fallback: convert to string
        return Const.of(value.toString());
    }

    private void forEachConstraint(BeanValidationMetadata metadata,
            Consumer<ConstraintMetadata> visitor) {
        for (ConstrainedBeanMetadata bean : metadata.beans().values()) {
            visitConstraints(bean.classConstraints(), visitor);
            for (ConstrainedFieldMetadata field : bean.fields()) {
                visitConstraints(field.constraints(), visitor);
                visitContainerElementConstraints(field.containerElementConstraints(), visitor);
            }
            for (ConstrainedMethodMetadata method : bean.methods()) {
                visitConstraints(method.returnValueConstraints(), visitor);
                visitConstraints(method.crossParameterConstraints(), visitor);
                visitContainerElementConstraints(method.returnValueContainerElementConstraints(), visitor);
                for (ConstrainedParameterMetadata param : method.parameters()) {
                    visitConstraints(param.constraints(), visitor);
                    visitContainerElementConstraints(param.containerElementConstraints(), visitor);
                }
            }
            for (ConstrainedMethodMetadata ctor : bean.constructors()) {
                visitConstraints(ctor.returnValueConstraints(), visitor);
                visitConstraints(ctor.crossParameterConstraints(), visitor);
                visitContainerElementConstraints(ctor.returnValueContainerElementConstraints(), visitor);
                for (ConstrainedParameterMetadata param : ctor.parameters()) {
                    visitConstraints(param.constraints(), visitor);
                    visitContainerElementConstraints(param.containerElementConstraints(), visitor);
                }
            }
        }
    }

    private void visitContainerElementConstraints(List<ContainerElementConstraint> containerElements,
            Consumer<ConstraintMetadata> visitor) {
        if (containerElements == null) {
            return;
        }
        for (ContainerElementConstraint cec : containerElements) {
            visitConstraints(cec.constraints(), visitor);
            visitContainerElementConstraints(cec.nestedContainerElements(), visitor);
        }
    }

    private void visitConstraints(java.util.List<ConstraintMetadata> constraints,
            Consumer<ConstraintMetadata> visitor) {
        if (constraints == null) {
            return;
        }
        for (ConstraintMetadata constraint : constraints) {
            visitor.accept(constraint);
            visitConstraints(constraint.composingConstraints(), visitor);
        }
    }

    private void registerForReflection(BeanValidationMetadata metadata,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // Register all built-in constraint validator classes (needed for native image
        // since ValidatorResolver returns null for built-ins and they are resolved at runtime)
        Set<String> classNames = new HashSet<>(BuiltinConstraintValidators.getAllValidatorClassNames());

        for (ConstrainedBeanMetadata bean : metadata.beans().values()) {
            classNames.add(bean.className());
        }

        forEachConstraint(metadata, constraint -> {
            if (constraint.validatorClassName() != null) {
                classNames.add(constraint.validatorClassName());
            }
            if (constraint.annotationClassName() != null) {
                classNames.add(constraint.annotationClassName());
            }
        });

        if (!classNames.isEmpty()) {
            reflectiveClasses.produce(
                    ReflectiveClassBuildItem.builder(classNames.toArray(new String[0]))
                            .fields(true)
                            .methods(true)
                            .constructors(true)
                            .build());
        }
    }

    private Map<String, String> generateAnnotationLiterals(
            BeanValidationMetadata metadata,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // Collect all unique annotation class names from metadata
        Set<String> annotationClassNames = new HashSet<>();
        collectAnnotationClassNames(metadata, annotationClassNames);

        if (annotationClassNames.isEmpty()) {
            return Map.of();
        }

        // Generate a literal class for each annotation type
        // Use applicationClass=true so that generated literals can see annotation classes
        // from the application/test classloader (e.g. custom constraint annotations defined in tests)
        ClassOutput classOutput = new GeneratedClassGizmo2Adaptor(generatedClasses, generatedResources, true);
        ConstraintAnnotationLiteralGenerator generator = new ConstraintAnnotationLiteralGenerator();
        Map<String, String> annotationToLiteral = new HashMap<>();

        for (String annotationFqcn : annotationClassNames) {
            String literalFqcn = generator.generate(annotationFqcn, combinedIndex.getIndex(), classOutput);
            annotationToLiteral.put(annotationFqcn, literalFqcn);
        }

        // Register generated literal classes for reflection (constructor access in native)
        if (!annotationToLiteral.isEmpty()) {
            reflectiveClasses.produce(
                    ReflectiveClassBuildItem.builder(annotationToLiteral.values().toArray(new String[0]))
                            .constructors(true)
                            .build());
        }

        return annotationToLiteral;
    }

    private Map<String, String> generatePropertyAccessors(
            BeanValidationMetadata metadata,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // Use applicationClass=true so generated classes are loaded by the app classloader
        // (they reference application bean classes via CHECKCAST/GETFIELD/INVOKEVIRTUAL)
        ClassOutput classOutput = new GeneratedClassGizmo2Adaptor(generatedClasses, generatedResources, true);
        PropertyAccessorGenerator generator = new PropertyAccessorGenerator();
        Map<String, String> beanToAccessor = new HashMap<>();

        for (ConstrainedBeanMetadata bean : metadata.beans().values()) {
            // Skip beans with no fields and no getters
            boolean hasFields = !bean.fields().isEmpty();
            boolean hasGetters = bean.methods().stream().anyMatch(ConstrainedMethodMetadata::getter);
            if (!hasFields && !hasGetters) {
                continue;
            }

            String accessorFqcn = generator.generate(bean, combinedIndex.getIndex(), classOutput);
            beanToAccessor.put(bean.className(), accessorFqcn);
        }

        // Register generated accessor classes for reflection (constructor access in native)
        if (!beanToAccessor.isEmpty()) {
            reflectiveClasses.produce(
                    ReflectiveClassBuildItem.builder(beanToAccessor.values().toArray(new String[0]))
                            .constructors(true)
                            .build());
        }

        return beanToAccessor;
    }

    private String generateValidatorInstantiator(
            BeanValidationMetadata metadata,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // Collect all validator class names: built-in + custom from constraint mappings
        Set<String> validatorClassNames = new HashSet<>(BuiltinConstraintValidators.getAllValidatorClassNames());
        Map<String, List<String>> mapping = metadata.constraintValidatorMapping();
        if (mapping != null) {
            for (List<String> validators : mapping.values()) {
                validatorClassNames.addAll(validators);
            }
        }

        if (validatorClassNames.isEmpty()) {
            return null;
        }

        ClassOutput classOutput = new GeneratedClassGizmo2Adaptor(generatedClasses, generatedResources, false);
        ValidatorInstantiatorGenerator generator = new ValidatorInstantiatorGenerator();
        generator.generate(validatorClassNames, classOutput);

        reflectiveClasses.produce(
                ReflectiveClassBuildItem.builder(ValidatorInstantiatorGenerator.GENERATED_CLASS_NAME)
                        .constructors(true)
                        .build());

        return ValidatorInstantiatorGenerator.GENERATED_CLASS_NAME;
    }

    private void collectAnnotationClassNames(BeanValidationMetadata metadata, Set<String> annotationClassNames) {
        forEachConstraint(metadata, constraint -> {
            if (constraint.annotationClassName() != null) {
                annotationClassNames.add(constraint.annotationClassName());
            }
        });
    }

    private void registerMetadataTypes(RecorderContext ctx) throws Exception {
        ctx.registerNonDefaultConstructor(
                BeanValidationMetadata.class.getDeclaredConstructor(
                        java.util.Map.class, java.util.Set.class,
                        java.util.Set.class, java.util.Map.class,
                        java.util.Set.class, java.util.Map.class,
                        java.util.Map.class, java.util.Map.class,
                        java.util.Map.class, java.util.Map.class,
                        java.util.Map.class),
                m -> Arrays.asList(
                        m.beans(), m.validatedConstraintDefinitions(),
                        m.crossParameterValidatorClassNames(), m.interfaceGroupSequences(),
                        m.convertGroupValidatedBeans(), m.validatorTargetTypes(),
                        m.constraintValidatorMapping(), m.invalidConstraintDefinitions(),
                        m.invalidConstraintDeclarations(), m.convertGroupDuplicateErrors(),
                        m.iterableTypeArguments()));

        ctx.registerNonDefaultConstructor(
                ConstrainedBeanMetadata.class.getDeclaredConstructor(
                        String.class, java.util.List.class, java.util.List.class,
                        java.util.List.class, java.util.List.class, java.util.List.class,
                        java.util.List.class, java.util.Set.class),
                b -> Arrays.asList(
                        b.className(), b.classConstraints(), b.fields(),
                        b.methods(), b.constructors(), b.groupSequence(),
                        b.typeHierarchy(), b.allPropertyNames()));

        ctx.registerNonDefaultConstructor(
                ConstrainedFieldMetadata.class.getDeclaredConstructor(
                        String.class, String.class, String.class, java.util.List.class,
                        boolean.class, java.util.List.class, java.util.Map.class),
                f -> Arrays.asList(
                        f.name(), f.declaringClassName(), f.fieldTypeName(),
                        f.constraints(), f.cascading(), f.containerElementConstraints(),
                        f.groupConversions()));

        ctx.registerNonDefaultConstructor(
                ConstrainedMethodMetadata.class.getDeclaredConstructor(
                        String.class, String.class, String.class, java.util.List.class,
                        java.util.List.class, java.util.List.class,
                        boolean.class, java.util.List.class, java.util.List.class,
                        java.util.Map.class, boolean.class, boolean.class),
                m -> Arrays.asList(
                        m.name(), m.declaringClassName(), m.returnTypeName(),
                        m.parameterTypeNames(), m.parameters(),
                        m.returnValueConstraints(), m.returnValueCascading(),
                        m.crossParameterConstraints(), m.returnValueContainerElementConstraints(),
                        m.returnValueGroupConversions(), m.constructor(), m.getter()));

        ctx.registerNonDefaultConstructor(
                ConstrainedParameterMetadata.class.getDeclaredConstructor(
                        String.class, int.class, String.class, java.util.List.class,
                        boolean.class, java.util.List.class, java.util.Map.class),
                p -> Arrays.asList(
                        p.name(), p.index(), p.typeName(), p.constraints(),
                        p.cascading(), p.containerElementConstraints(), p.groupConversions()));

        ctx.registerNonDefaultConstructor(
                ConstraintMetadata.class.getDeclaredConstructor(
                        String.class, java.util.Map.class, java.util.List.class,
                        java.util.List.class, String.class, boolean.class, java.util.List.class,
                        java.util.Map.class, String.class, java.util.List.class, java.util.Map.class),
                c -> Arrays.asList(
                        c.annotationClassName(), c.attributes(), c.groups(),
                        c.payload(), c.validatorClassName(), c.reportAsSingleViolation(),
                        c.composingConstraints(), c.overridesAttributes(),
                        c.defaultMessageTemplate(), c.allValidatorClassNames(), c.attributeTypes()));

        ctx.registerNonDefaultConstructor(
                ContainerElementConstraint.class.getDeclaredConstructor(
                        int.class, java.util.List.class, boolean.class, String.class,
                        java.util.Map.class, java.util.List.class),
                e -> Arrays.asList(
                        e.typeArgumentIndex(), e.constraints(),
                        e.cascading(), e.containerClassName(),
                        e.groupConversions(), e.nestedContainerElements()));
    }
}
