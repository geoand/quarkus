package io.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ModuleOpenBuildItem;

public class JavaOpensProcessor {

    @BuildStep
    ModuleOpenBuildItem open() {
        return new ModuleOpenBuildItem("java.base", "io.quarkus", "jdk.internal.loader");
    }
}
