package io.quarkus.bean.validation.arquillian;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.DeploymentScoped;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.arquillian.utils.Archives;
import io.quarkus.bean.validation.arquillian.utils.ClassLoading;
import io.quarkus.bean.validation.arquillian.utils.Directories;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.processor.BeanValidationProcessor;

public class BvDeployableContainer implements DeployableContainer<BvContainerConfiguration> {
    static Object testInstance;

    @Inject
    @DeploymentScoped
    private InstanceProducer<DeploymentDir> deploymentDir;

    @Inject
    @DeploymentScoped
    private InstanceProducer<DeploymentClassLoader> deploymentClassLoader;

    @Inject
    private Instance<TestClass> testClass;

    @Override
    public Class<BvContainerConfiguration> getConfigurationClass() {
        return BvContainerConfiguration.class;
    }

    @Override
    public void setup(BvContainerConfiguration configuration) {
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("BeanValidation");
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        if (testClass.get() == null) {
            throw new IllegalStateException("Test class not available");
        }

        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            DeploymentDir deploymentDir = new DeploymentDir();
            this.deploymentDir.set(deploymentDir);

            // Explode archive
            if (archive instanceof JavaArchive) {
                Archives.explode(archive, "/", deploymentDir.appClasses);
            } else if (archive instanceof WebArchive) {
                Archives.explode(archive, "/WEB-INF/classes/", deploymentDir.appClasses);
            } else {
                throw new DeploymentException("Unknown archive type: " + archive);
            }

            // Build Jandex index from exploded classes
            Index index = buildIndex(deploymentDir.appClasses);

            // Run BeanValidationProcessor
            BeanValidationProcessor processor = new BeanValidationProcessor();
            BeanValidationMetadata metadata = processor.process(index);

            // Configure the provider so Validation.buildDefaultValidatorFactory() picks up metadata
            QuarkusValidationProvider.configure(metadata, null, null);

            // Create deployment classloader and set as TCCL
            DeploymentClassLoader dcl = new DeploymentClassLoader(deploymentDir);
            this.deploymentClassLoader.set(dcl);
            Thread.currentThread().setContextClassLoader(dcl);

            // Create test instance from the DeploymentClassLoader
            String testClassName = testClass.get().getJavaClass().getName();
            Class<?> actualTestClass = Class.forName(testClassName, true, dcl);
            testInstance = actualTestClass.getDeclaredConstructor().newInstance();

        } catch (Throwable t) {
            Throwable nt = ClassLoading.cloneExceptionIntoSystemCL(t);
            throw new DeploymentException("Unable to deploy Bean Validation test", nt);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }

        return new ProtocolMetaData();
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            testInstance = null;
            QuarkusValidationProvider.clearConfiguration();

            DeploymentClassLoader dcl = this.deploymentClassLoader.get();
            if (dcl != null) {
                try {
                    dcl.close();
                } catch (IOException e) {
                    throw new DeploymentException("Failed to close deployment classloader", e);
                }
            }

            DeploymentDir dir = this.deploymentDir.get();
            if (dir != null) {
                if (System.getProperty("retainDeployment") == null) {
                    Directories.deleteDirectory(dir.root);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deploy(Descriptor descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void undeploy(Descriptor descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private Index buildIndex(Path classesDir) throws IOException {
        Indexer indexer = new Indexer();
        // Index exploded test classes
        try (Stream<Path> walk = Files.walk(classesDir)) {
            List<Path> classFiles = walk.filter(p -> p.toString().endsWith(".class"))
                    .toList();
            for (Path classFile : classFiles) {
                try (InputStream in = Files.newInputStream(classFile)) {
                    indexer.index(in);
                }
            }
        }
        // Index the jakarta.validation-api JAR so that built-in constraint annotations
        // (e.g., @Pattern, @Size) and their repeatable containers are available in the index
        indexJakartaValidationApi(indexer);
        return indexer.complete();
    }

    private void indexJakartaValidationApi(Indexer indexer) throws IOException {
        URL url = jakarta.validation.constraints.NotNull.class.getProtectionDomain()
                .getCodeSource().getLocation();
        if (url == null) {
            return;
        }
        String path = url.getPath();
        if (path.endsWith(".jar")) {
            try (JarFile jarFile = new JarFile(path)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        try (InputStream in = jarFile.getInputStream(entry)) {
                            indexer.index(in);
                        }
                    }
                }
            }
        } else {
            // Exploded directory (e.g., during development)
            Path dir = Path.of(path);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    List<Path> classFiles = walk.filter(p -> p.toString().endsWith(".class"))
                            .toList();
                    for (Path classFile : classFiles) {
                        try (InputStream in = Files.newInputStream(classFile)) {
                            indexer.index(in);
                        }
                    }
                }
            }
        }
    }
}
