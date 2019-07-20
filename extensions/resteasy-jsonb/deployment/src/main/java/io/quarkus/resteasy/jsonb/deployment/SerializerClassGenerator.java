package io.quarkus.resteasy.jsonb.deployment;

import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.resteasy.jsonb.deployment.serializers.GlobalSerializationConfig;
import io.quarkus.resteasy.jsonb.deployment.serializers.TypeSerializerGenerator;
import io.quarkus.resteasy.jsonb.deployment.serializers.TypeSerializerGeneratorRegistry;

public class SerializerClassGenerator {

    private final JsonbConfig jsonbConfig;

    public SerializerClassGenerator(JsonbConfig jsonbConfig) {
        this.jsonbConfig = jsonbConfig;
    }

    public Result generateSerializerForClassType(ClassType classType, TypeSerializerGeneratorRegistry registry,
            ClassOutput classOutput) {
        TypeSerializerGenerator.Supported supported = registry.getObjectSerializer().supports(classType, registry);
        if (supported == TypeSerializerGenerator.Supported.UNSUPPORTED) {
            return Result.notGenerated();
        }

        DotName classDotName = classType.name();
        String generatedSerializerName = "io.quarkus.jsonb.serializers." + classDotName.withoutPackagePrefix() + "Serializer";
        try (ClassCreator cc = ClassCreator.builder()
                .classOutput(classOutput).className(generatedSerializerName)
                .interfaces(JsonbSerializer.class)
                .signature(String.format("Ljava/lang/Object;Ljavax/json/bind/serializer/JsonbSerializer<L%s;>;",
                        classDotName.toString()).replace('.', '/'))
                .build()) {

            // actual implementation of serialize method
            try (MethodCreator serialize = cc.getMethodCreator("serialize", "void", classDotName.toString(),
                    JsonGenerator.class.getName(), SerializationContext.class.getName())) {
                ResultHandle object = serialize.getMethodParam(0);
                ResultHandle jsonGenerator = serialize.getMethodParam(1);
                ResultHandle serializationContext = serialize.getMethodParam(2);

                // delegate to object serializer
                registry.getObjectSerializer().generate(
                        new TypeSerializerGenerator.GenerateContext(classType, serialize, jsonGenerator, serializationContext,
                                object, registry,
                                getGlobalConfig(), false, null));

                serialize.returnValue(null);
            }

            // bridge method
            try (MethodCreator bridgeSerialize = cc.getMethodCreator("serialize", "void", Object.class, JsonGenerator.class,
                    SerializationContext.class)) {
                MethodDescriptor serialize = MethodDescriptor.ofMethod(generatedSerializerName, "serialize", "void",
                        classDotName.toString(),
                        JsonGenerator.class.getName(), SerializationContext.class.getName());
                ResultHandle castedObject = bridgeSerialize.checkCast(bridgeSerialize.getMethodParam(0),
                        classDotName.toString());
                bridgeSerialize.invokeVirtualMethod(serialize, bridgeSerialize.getThis(),
                        castedObject, bridgeSerialize.getMethodParam(1), bridgeSerialize.getMethodParam(2));
                bridgeSerialize.returnValue(null);
            }
        }

        return supported == TypeSerializerGenerator.Supported.FULLY
                ? Result.noReflectionNeeded(generatedSerializerName)
                : Result.reflectionNeeded(generatedSerializerName);
    }

    private GlobalSerializationConfig getGlobalConfig() {
        return new GlobalSerializationConfig(
                jsonbConfig.locale, jsonbConfig.dateFormat, jsonbConfig.serializeNullValues, jsonbConfig.propertyOrderStrategy);
    }

    static class Result {
        private final boolean generated;
        private final String className;
        private final boolean needsReflection;

        private Result(boolean generated, String className, boolean needsReflection) {
            this.generated = generated;
            this.className = className;
            this.needsReflection = needsReflection;
        }

        static Result notGenerated() {
            return new Result(false, null, false);
        }

        static Result noReflectionNeeded(String className) {
            return new Result(true, className, false);
        }

        static Result reflectionNeeded(String className) {
            return new Result(true, className, true);
        }

        boolean isGenerated() {
            return generated;
        }

        String getClassName() {
            return className;
        }

        boolean isNeedsReflection() {
            return needsReflection;
        }
    }
}
