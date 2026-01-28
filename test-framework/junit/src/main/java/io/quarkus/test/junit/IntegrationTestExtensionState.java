package io.quarkus.test.junit;

import static io.quarkus.test.common.PathTestHelper.getAppClassLocationForTestLocation;
import static io.quarkus.test.common.PathTestHelper.getTestClassesLocation;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;

import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.utils.BuildToolHelper;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.container.spi.ContainerImageBuilderBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.paths.PathList;
import io.quarkus.test.common.ListeningAddress;
import io.quarkus.test.common.PathTestHelper;
import io.quarkus.test.common.TestResourceManager;
import io.quarkus.value.registry.ValueRegistry;
import io.smallrye.config.SmallRyeConfig;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class IntegrationTestExtensionState extends QuarkusTestExtensionState {

    private final Class requiredTestClass;
    private final Optional<ListeningAddress> listeningAddress;
    private final Map<String, String> sysPropRestore;

    public IntegrationTestExtensionState(
            Class requiredTestClass,
            ValueRegistry valueRegistry,
            TestResourceManager testResourceManager,
            Closeable resource,
            Runnable clearCallbacks,
            Optional<ListeningAddress> listeningAddress,
            Map<String, String> sysPropRestore) {
        super(valueRegistry, testResourceManager, resource, clearCallbacks);
        this.requiredTestClass = requiredTestClass;
        this.listeningAddress = listeningAddress;
        this.sysPropRestore = sysPropRestore;
    }

    public Optional<ListeningAddress> getListeningAddress() {
        return listeningAddress;
    }

    @Override
    protected void doClose() throws IOException {
        testResourceManager.close();
        resource.close();
        for (Map.Entry<String, String> entry : sysPropRestore.entrySet()) {
            String val = entry.getValue();
            if (val == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), val);
            }
        }
        // recalculate the property names that may have changed with the restore
        ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getLatestPropertyNames();

        // TODO: add proper support - this is only done to show what's possible and it doesn't even work properly at this point because of CL issues
        try {
            wip();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void wip() throws IOException, AppModelResolverException {
        // copied from handleDevServices

        Path testClassLocation = getTestClassesLocation(requiredTestClass);
        final Path appClassLocation = getAppClassLocationForTestLocation(testClassLocation);

        final PathList.Builder rootBuilder = PathList.builder();

        if (!appClassLocation.equals(testClassLocation)) {
            rootBuilder.add(testClassLocation);
            // if test classes is a dir, we should also check whether test resources dir exists as a separate dir (gradle)
            // TODO: this whole app/test path resolution logic is pretty dumb, it needs be re-worked using proper workspace discovery
            final Path testResourcesLocation = PathTestHelper.getResourcesForClassesDirOrNull(testClassLocation,
                    "test");
            if (testResourcesLocation != null) {
                rootBuilder.add(testResourcesLocation);
            }
        }
        final QuarkusBootstrap.Builder runnerBuilder = QuarkusBootstrap.builder()
                .setIsolateDeployment(true)
                .setMode(QuarkusBootstrap.Mode.TEST); // TODO: we need PROD here but that causes a CCE

        final Path projectRoot = Paths.get("").normalize().toAbsolutePath();
        runnerBuilder.setProjectRoot(projectRoot);
        runnerBuilder.setTargetDirectory(PathTestHelper.getProjectBuildDir(projectRoot, testClassLocation));

        if (Files.exists(appClassLocation)) {
            rootBuilder.add(appClassLocation);
        }
        final Path appResourcesLocation = PathTestHelper.getResourcesForClassesDirOrNull(appClassLocation, "main");
        if (appResourcesLocation != null) {
            if (Files.exists(appResourcesLocation)) {
                rootBuilder.add(appResourcesLocation);
            }
        }

        // If gradle project running directly with IDE
        if (System.getProperty(BootstrapConstants.SERIALIZED_TEST_APP_MODEL) == null) {
            ApplicationModel model = BuildToolHelper.enableGradleAppModelForTest(projectRoot);
            if (model != null && model.getApplicationModule() != null) {
                final ArtifactSources testSources = model.getApplicationModule().getTestSources();
                if (testSources != null) {
                    for (SourceDir src : testSources.getSourceDirs()) {
                        if (!Files.exists(src.getOutputDir())) {
                            final Path classes = src.getOutputDir();
                            if (!rootBuilder.contains(classes)) {
                                rootBuilder.add(classes);
                            }
                        }
                    }
                }
                for (SourceDir src : model.getApplicationModule().getMainSources().getSourceDirs()) {
                    if (!Files.exists(src.getOutputDir())) {
                        final Path classes = src.getOutputDir();
                        if (!rootBuilder.contains(classes)) {
                            rootBuilder.add(classes);
                        }
                    }
                }
            }
        } else if (System.getProperty(BootstrapConstants.OUTPUT_SOURCES_DIR) != null) {
            final String[] sourceDirectories = System.getProperty(BootstrapConstants.OUTPUT_SOURCES_DIR).split(",");
            for (String sourceDirectory : sourceDirectories) {
                final Path directory = Paths.get(sourceDirectory);
                if (Files.exists(directory) && !rootBuilder.contains(directory)) {
                    rootBuilder.add(directory);
                }
            }
        }
        runnerBuilder.setApplicationRoot(rootBuilder.build());

        // Set the config profile and properties overrides from the @TestProfile also for DevServices augmentation
        Properties properties = new Properties();
        // Ensure that these properties cannot be overridden
        properties.put(ConfigSource.CONFIG_ORDINAL, Integer.MAX_VALUE);

        //                Index testClassesIndex = TestClassIndexer.indexTestClasses(requiredTestClass);
        //                // we need to write the Index to make it reusable from other parts of the testing infrastructure that run in different ClassLoaders
        //                TestClassIndexer.writeIndex(testClassesIndex, requiredTestClass);

        try (CuratedApplication curatedApplication = runnerBuilder
                .setTest(false)
                .setBuildSystemProperties(properties)
                .build()
                .bootstrap()) {
            AugmentAction action = curatedApplication.createAugmentor();
            action.performCustomBuild(ContainerImageBuildDeclarationHandler.class.getName(), null,
                    ContainerImageBuilderBuildItem.class.getName(), ArtifactResultBuildItem.class.getName());
        } catch (BootstrapException ex) {
            throw new RuntimeException(ex);
        }
    }
}
