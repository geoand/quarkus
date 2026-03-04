package io.quarkus.bean.validation.arquillian;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class DeploymentDir {
    final Path root;
    final Path appClasses;

    DeploymentDir() throws IOException {
        this.root = Files.createTempDirectory("BvArquillian");
        this.appClasses = Files.createDirectories(root.resolve("app").resolve("classes"));
    }
}
