package io.quarkus.resteasy.jsonb.deployment.serializers;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.json.bind.config.PropertyOrderStrategy;
import javax.json.stream.JsonGenerator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.resteasy.jsonb.deployment.DotNames;
import io.quarkus.resteasy.jsonb.deployment.PropertyUtil;
import io.quarkus.resteasy.jsonb.deployment.SerializationClassInspector;

public class ObjectTypeSerializerGenerator extends AbstractTypeSerializerGenerator {

    @Override
    public Supported supports(Type type, TypeSerializerGeneratorRegistry registry) {
        if (type instanceof ArrayType) {
            return Supported.UNSUPPORTED;
        }

        if (type.name().toString().startsWith("java")) {
            return Supported.UNSUPPORTED;
        }

        final SerializationClassInspector.Result inspectionsResult = registry.getInspector().inspect(type.name());
        if (!inspectionsResult.isPossible()) {
            return Supported.UNSUPPORTED;
        }

        boolean foundUnhandledType = false;
        for (MethodInfo getter : inspectionsResult.getGetters().keySet()) {
            if (registry.correspondingTypeSerializer(getter.returnType()) == null) {
                if (canUseUnhandledTypeGenerator(getter.returnType())) {
                    foundUnhandledType = true;
                } else {
                    return Supported.UNSUPPORTED;
                }
            }
        }

        for (FieldInfo field : inspectionsResult.getVisibleFieldsWithoutGetters()) {
            if (registry.correspondingTypeSerializer(field.type()) == null) {
                if (canUseUnhandledTypeGenerator(field.type())) {
                    foundUnhandledType = true;
                } else {
                    return Supported.UNSUPPORTED;
                }
            }
        }

        return foundUnhandledType ? Supported.WITH_UNHANDLED : Supported.FULLY;
    }

    private boolean canUseUnhandledTypeGenerator(Type type) {
        // Parameterized types are unsupported because we don't have the proper Yasson metadata
        // to tell Yasson the serializer to use
        return type instanceof ClassType || type instanceof ArrayType;
    }

    @Override
    protected void generateNotNull(GenerateContext context) {
        BytecodeCreator bytecodeCreator = context.getBytecodeCreator();
        ResultHandle jsonGenerator = context.getJsonGenerator();

        TypeSerializerGeneratorRegistry serializerRegistry = context.getRegistry();
        DotName classDotNate = context.getType().name();
        SerializationClassInspector.Result inspectionResult = serializerRegistry.getInspector()
                .inspect(classDotNate);
        if (!inspectionResult.isPossible()) {
            // should never happen when used property (meaning that supports is called before this method)
            throw new IllegalStateException("Could not generate serializer for " + classDotNate);
        }

        // if the type is an interface, we need to cast to the actual type that will be used
        ClassInfo classInfo = context.getRegistry().getIndex().getClassByName(context.getType().name());
        if (Modifier.isInterface(classInfo.flags())) {
            ClassInfo concreteType = inspectionResult.getClassInfo();
            ResultHandle castedToConcrete = bytecodeCreator.checkCast(context.getCurrentItem(), concreteType.name().toString());
            context = context.changeItem(Type.create(concreteType.name(), Type.Kind.CLASS), castedToConcrete);
        }

        bytecodeCreator.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(JsonGenerator.class, "writeStartObject", JsonGenerator.class), jsonGenerator);

        // instead of generating the bytecode for each property right away, we instead introduce
        // a Generator interface that will do the job on lazily
        // this allows us to add the keys from both getters and fields and have them both sorted
        // using the proper strategy
        SortedMap<String, Generator<?>> propertyNameToGenerator = PropertyOrderStrategy.REVERSE
                .equalsIgnoreCase(context.getGlobalConfig().getPropertyOrderStrategy())
                        ? new TreeMap<>(Collections.reverseOrder())
                        : new TreeMap<>(); //use lexicographical order by default
        Map<String, String> defaultToFinaKeyName = new HashMap<>();
        Map<String, String> finalToDefaultKeyName = new HashMap<>();

        // setup getter generation
        for (Map.Entry<MethodInfo, FieldInfo> entry : inspectionResult.getGetters().entrySet()) {
            MethodInfo getterMethodInfo = entry.getKey();
            FieldInfo fieldInfo = entry.getValue();
            String defaultKeyName = PropertyUtil.toFieldName(getterMethodInfo);
            Type returnType = getterMethodInfo.returnType();
            TypeSerializerGenerator getterTypeSerializerGenerator = serializerRegistry.correspondingTypeSerializer(returnType);
            if (getterTypeSerializerGenerator == null) {
                if (canUseUnhandledTypeGenerator(returnType)) {
                    getterTypeSerializerGenerator = new UnhandledTypeGenerator(context.getType(), defaultKeyName);
                } else {
                    throw new IllegalStateException("Could not generate serializer for getter " + getterMethodInfo.name()
                            + " of type " + classDotNate);
                }
            }

            Map<DotName, AnnotationInstance> effectiveGetterAnnotations = getEffectiveGetterAnnotations(getterMethodInfo,
                    fieldInfo, serializerRegistry.getInspector());
            String finalKeyName = getFinalKeyName(defaultKeyName, effectiveGetterAnnotations);

            defaultToFinaKeyName.put(defaultKeyName, finalKeyName);
            finalToDefaultKeyName.put(finalKeyName, defaultKeyName);

            boolean isNillable = isPropertyNillable(effectiveGetterAnnotations.get(DotNames.JSONB_PROPERTY),
                    context.getGlobalConfig(), inspectionResult);

            propertyNameToGenerator.put(
                    finalKeyName,
                    new GetterGenerator(new GeneratorInput<>(
                            context, getterMethodInfo, fieldInfo, getterTypeSerializerGenerator, finalKeyName, isNillable)));
        }

        // setup field generation
        for (FieldInfo fieldInfo : inspectionResult.getVisibleFieldsWithoutGetters()) {
            Type fieldType = fieldInfo.type();
            String defaultKeyName = fieldInfo.name();
            TypeSerializerGenerator getterTypeSerializerGenerator = serializerRegistry.correspondingTypeSerializer(fieldType);
            if (getterTypeSerializerGenerator == null) {
                if (canUseUnhandledTypeGenerator(fieldType)) {
                    getterTypeSerializerGenerator = new UnhandledTypeGenerator(context.getType(), defaultKeyName);
                } else {
                    throw new IllegalStateException("Could not generate serializer for field " + defaultKeyName
                            + " of type " + classDotNate);
                }
            }

            Map<DotName, AnnotationInstance> effectiveGetterAnnotations = getEffectiveFieldAnnotations(fieldInfo,
                    serializerRegistry.getInspector());
            String finalKeyName = getFinalKeyName(defaultKeyName, effectiveGetterAnnotations);

            defaultToFinaKeyName.put(defaultKeyName, finalKeyName);
            finalToDefaultKeyName.put(finalKeyName, defaultKeyName);

            boolean isNillable = isPropertyNillable(effectiveGetterAnnotations.get(DotNames.JSONB_PROPERTY),
                    context.getGlobalConfig(), inspectionResult);

            propertyNameToGenerator.put(
                    finalKeyName,
                    new FieldGenerator(new GeneratorInput<>(
                            context, fieldInfo, null, getterTypeSerializerGenerator, finalKeyName, isNillable)));
        }

        // TODO handle @JsonbPropertyOrder meta-annotations
        // setup the properties in the correct order if the @JsonbPropertyOrder annotation is used
        if (inspectionResult.getEffectiveClassAnnotations().containsKey(DotNames.JSONB_PROPERTY_ORDER)) {
            LinkedHashSet<String> customOrder = new LinkedHashSet<>();
            AnnotationInstance annotationInstance = inspectionResult.getEffectiveClassAnnotations()
                    .get(DotNames.JSONB_PROPERTY_ORDER);
            AnnotationValue value = annotationInstance.value();
            if (value != null) {
                customOrder.addAll(Arrays.asList(value.asStringArray()));
            }

            // JSON-B specifies that the values of the @JsonbPropertyOrder annotation are the original java property names
            // before any customizations are applied
            for (String propertyName : customOrder) {
                if (defaultToFinaKeyName.containsKey(propertyName)) {
                    String finalKeyName = defaultToFinaKeyName.get(propertyName);
                    if (propertyNameToGenerator.containsKey(finalKeyName)) {
                        propertyNameToGenerator.get(finalKeyName).generate();
                    }
                }
            }
            // go through the properties and serialize the ones that weren't already serialized
            for (Map.Entry<String, Generator<?>> entry : propertyNameToGenerator.entrySet()) {
                String defaultName = finalToDefaultKeyName.get(entry.getKey());
                if (!customOrder.contains(defaultName)) {
                    entry.getValue().generate();
                }
            }
        } else {
            // at this point the properties are sorted so we can just generate the bytecode
            for (Generator<?> generator : propertyNameToGenerator.values()) {
                generator.generate();
            }
        }

        bytecodeCreator.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(JsonGenerator.class, "writeEnd", JsonGenerator.class), jsonGenerator);
    }

    private String getFinalKeyName(String defaultKeyName, Map<DotName, AnnotationInstance> effectiveGetterAnnotations) {
        String finalKeyName = defaultKeyName;
        if (effectiveGetterAnnotations.containsKey(DotNames.JSONB_PROPERTY)) {
            AnnotationInstance instance = effectiveGetterAnnotations.get(DotNames.JSONB_PROPERTY);
            AnnotationValue value = instance.value();
            if (value != null) {
                String valueStr = value.asString();
                if ((valueStr != null) && !valueStr.isEmpty()) {
                    finalKeyName = valueStr;
                }
            }
        }
        return finalKeyName;
    }

    /**
     * Determine if the property is nillable.
     * Priorities are from highest to lowest:
     * - @JsonbProperty on the method
     * - @JsonbProperty on the field
     * - @JsonbNillable on the class (or anywhere in the class hierarchy if it's not directly on the class)
     * - @JsonbNillable on the package
     * - global configuration
     */
    private boolean isPropertyNillable(AnnotationInstance jsonbPropertyInstance, GlobalSerializationConfig globalConfig,
            SerializationClassInspector.Result inspectionResult) {
        boolean isNillable = globalConfig.isSerializeNullValues(); // use the global configuration as the default
        if (jsonbPropertyInstance != null) {
            AnnotationValue value = jsonbPropertyInstance.value("nillable");
            if (value != null) {
                // use whatever was specified on the method or field
                isNillable = value.asBoolean();
            }
        } else if (inspectionResult.getEffectiveClassAnnotations().containsKey(DotNames.JSONB_NILLABLE)) {
            isNillable = true; // set the default value of @JsonbNillable since it was used
            AnnotationValue value = inspectionResult.getEffectiveClassAnnotations().get(DotNames.JSONB_NILLABLE).value();
            if (value != null) {
                isNillable = value.asBoolean();
            }
        }
        return isNillable;
    }

    private static void writeKey(BytecodeCreator bytecodeCreator, ResultHandle jsonGenerator, String finalNameOfKey) {
        bytecodeCreator.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(JsonGenerator.class, "writeKey", JsonGenerator.class, String.class),
                jsonGenerator,
                bytecodeCreator.load(finalNameOfKey));
    }

    private static Map<DotName, AnnotationInstance> getEffectiveGetterAnnotations(MethodInfo getterMethodInfo,
            FieldInfo fieldInfo, SerializationClassInspector inspector) {
        Map<DotName, AnnotationInstance> result = new HashMap<>();
        for (AnnotationInstance annotationInstance : getterMethodInfo.annotations()) {
            result.put(annotationInstance.name(), annotationInstance);
        }

        if (fieldInfo != null) {
            for (AnnotationInstance annotationInstance : fieldInfo.annotations()) {
                if (!result.containsKey(annotationInstance.name())) {
                    result.put(annotationInstance.name(), annotationInstance);
                }
            }
        }

        addEffectiveClassAnnotations(inspector, getterMethodInfo.declaringClass(), result);

        return result;
    }

    private static Map<DotName, AnnotationInstance> getEffectiveFieldAnnotations(FieldInfo fieldInfo,
            SerializationClassInspector inspector) {
        Map<DotName, AnnotationInstance> result = new HashMap<>();

        for (AnnotationInstance annotationInstance : fieldInfo.annotations()) {
            if (!result.containsKey(annotationInstance.name())) {
                result.put(annotationInstance.name(), annotationInstance);
            }
        }

        addEffectiveClassAnnotations(inspector, fieldInfo.declaringClass(), result);

        return result;
    }

    private static void addEffectiveClassAnnotations(SerializationClassInspector inspector, ClassInfo classInfo,
            Map<DotName, AnnotationInstance> result) {
        Map<DotName, AnnotationInstance> effectiveClassAnnotations = inspector.inspect(classInfo.name())
                .getEffectiveClassAnnotations();
        for (DotName classAnnotationDotName : effectiveClassAnnotations.keySet()) {
            if (!result.containsKey(classAnnotationDotName)) {
                result.put(classAnnotationDotName, effectiveClassAnnotations.get(classAnnotationDotName));
            }
        }
    }

    private static class GeneratorInput<T extends AnnotationTarget> {
        private final GenerateContext context;
        private final T instanceInfo;
        private final AnnotationTarget associatedInstanceInfo;
        private final TypeSerializerGenerator typeSerializerGenerator;
        private final String finalKeyName;
        private final boolean isNillable;

        GeneratorInput(GenerateContext context, T instanceInfo, AnnotationTarget associatedInstanceInfo,
                TypeSerializerGenerator typeSerializerGenerator, String finalKeyName, boolean isNillable) {
            this.context = context;
            this.instanceInfo = instanceInfo;
            this.associatedInstanceInfo = associatedInstanceInfo;
            this.typeSerializerGenerator = typeSerializerGenerator;
            this.finalKeyName = finalKeyName;
            this.isNillable = isNillable;
        }

        GenerateContext getContext() {
            return context;
        }

        T getInstanceInfo() {
            return instanceInfo;
        }

        AnnotationTarget getAssociatedInstanceInfo() {
            return associatedInstanceInfo;
        }

        TypeSerializerGenerator getTypeSerializerGenerator() {
            return typeSerializerGenerator;
        }

        String getFinalKeyName() {
            return finalKeyName;
        }

        boolean isNillable() {
            return isNillable;
        }
    }

    private interface Generator<T extends GeneratorInput<?>> {
        void generate();
    }

    private static class GetterGenerator implements Generator<GeneratorInput<MethodInfo>> {

        private GeneratorInput<MethodInfo> input;

        GetterGenerator(GeneratorInput<MethodInfo> input) {
            this.input = input;
        }

        @Override
        public void generate() {
            BytecodeCreator bytecodeCreator = input.getContext().getBytecodeCreator();
            ResultHandle jsonGenerator = input.getContext().getJsonGenerator();
            DotName classDotNate = input.getContext().getType().name();
            MethodInfo getterMethodInfo = input.getInstanceInfo();
            Type returnType = getterMethodInfo.returnType();
            TypeSerializerGenerator getterTypeSerializerGenerator = input.getTypeSerializerGenerator();

            // ResultHandle of the getter method
            ResultHandle getter = bytecodeCreator.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(classDotNate.toString(), getterMethodInfo.name(),
                            returnType.name().toString()),
                    input.getContext().getCurrentItem());

            Map<DotName, AnnotationInstance> effectivePropertyAnnotations = getEffectiveGetterAnnotations(getterMethodInfo,
                    (FieldInfo) input.getAssociatedInstanceInfo(), input.getContext().getRegistry().getInspector());
            if (input.isNillable()) {
                writeKey(bytecodeCreator, jsonGenerator, input.getFinalKeyName());
                getterTypeSerializerGenerator.generate(input.getContext().changeItem(
                        bytecodeCreator, returnType, getter, false, effectivePropertyAnnotations));
            } else {
                // in this case we only write the property and value if the value is not null
                BytecodeCreator getterNotNull = bytecodeCreator.ifNull(getter).falseBranch();
                if (DotNames.OPTIONAL.equals(returnType.name())) {
                    ResultHandle isPresent = getterNotNull.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(Optional.class, "isPresent", boolean.class),
                            getter);

                    getterNotNull = getterNotNull.ifNonZero(isPresent).trueBranch();
                }

                writeKey(getterNotNull, jsonGenerator, input.getFinalKeyName());
                getterTypeSerializerGenerator.generate(input.getContext().changeItem(getterNotNull,
                        returnType, getter, true, effectivePropertyAnnotations));
            }
        }
    }

    private static class FieldGenerator implements Generator<GeneratorInput<FieldInfo>> {

        private GeneratorInput<FieldInfo> input;

        FieldGenerator(GeneratorInput<FieldInfo> input) {
            this.input = input;
        }

        @Override
        public void generate() {
            BytecodeCreator bytecodeCreator = input.getContext().getBytecodeCreator();
            ResultHandle jsonGenerator = input.getContext().getJsonGenerator();
            DotName classDotNate = input.getContext().getType().name();
            FieldInfo fieldInfo = input.getInstanceInfo();
            Type fieldType = fieldInfo.type();
            TypeSerializerGenerator fieldTypeSerializerGenerator = input.getTypeSerializerGenerator();

            ResultHandle field = bytecodeCreator.readInstanceField(
                    FieldDescriptor.of(classDotNate.toString(), fieldInfo.name(),
                            fieldType.name().toString()),
                    input.getContext().getCurrentItem());

            Map<DotName, AnnotationInstance> effectivePropertyAnnotations = getEffectiveFieldAnnotations(fieldInfo,
                    input.getContext().getRegistry().getInspector());
            if (input.isNillable()) {
                writeKey(bytecodeCreator, jsonGenerator, input.getFinalKeyName());
                fieldTypeSerializerGenerator.generate(input.getContext().changeItem(
                        bytecodeCreator, fieldType, field, false, effectivePropertyAnnotations));
            } else {
                // in this case we only write the property and value if the value is not null
                BytecodeCreator fieldNotNull = bytecodeCreator.ifNull(field).falseBranch();
                if (DotNames.OPTIONAL.equals(fieldType.name())) {
                    ResultHandle isPresent = fieldNotNull.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(Optional.class, "isPresent", boolean.class),
                            field);

                    fieldNotNull = fieldNotNull.ifNonZero(isPresent).trueBranch();
                }

                writeKey(fieldNotNull, jsonGenerator, input.getFinalKeyName());
                fieldTypeSerializerGenerator.generate(input.getContext().changeItem(fieldNotNull,
                        fieldType, field, true, effectivePropertyAnnotations));
            }
        }
    }

}
