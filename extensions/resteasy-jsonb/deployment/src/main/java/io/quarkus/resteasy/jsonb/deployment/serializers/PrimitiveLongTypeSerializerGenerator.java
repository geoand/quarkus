package io.quarkus.resteasy.jsonb.deployment.serializers;

import javax.json.stream.JsonGenerator;

import org.jboss.jandex.Type;

import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.resteasy.jsonb.deployment.DotNames;

public class PrimitiveLongTypeSerializerGenerator extends AbstractNumberTypeSerializerGenerator {

    @Override
    public Supported supports(Type type, TypeSerializerGeneratorRegistry registry) {
        return DotNames.PRIMITIVE_LONG.equals(type.name()) ? Supported.FULLY : Supported.UNSUPPORTED;
    }

    @Override
    public void generateUnformatted(GenerateContext context) {
        BytecodeCreator bytecodeCreator = context.getBytecodeCreator();
        bytecodeCreator.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(JsonGenerator.class, "write", JsonGenerator.class, long.class),
                context.getJsonGenerator(),
                context.getCurrentItem());
    }

}
