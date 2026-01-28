package io.quarkus.test.junit;

import java.util.function.BiConsumer;

import io.quarkus.builder.BuildResult;
import io.quarkus.container.spi.ContainerImageBuilderBuildItem;

public class ContainerImageBuildDeclarationHandler implements BiConsumer<Object, BuildResult> {
    @Override
    public void accept(Object context, BuildResult buildResult) {
        var result = buildResult.consumeMulti(ContainerImageBuilderBuildItem.class);
        // TODO: do something
    }
}
