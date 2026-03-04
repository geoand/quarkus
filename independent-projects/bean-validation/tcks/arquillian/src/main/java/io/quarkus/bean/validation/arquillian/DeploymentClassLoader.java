package io.quarkus.bean.validation.arquillian;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Loads the test classes for a Bean Validation Arquillian test. There's one
 * {@code DeploymentClassLoader} for each test, which is closed at the end.
 * <p>
 * The delegation model of this class loader is "child first". That is, this class loader
 * attempts to find the requested class on its own (which succeeds for the test classes)
 * and it only delegates to the parent if it fails.
 */
final class DeploymentClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    DeploymentClassLoader(DeploymentDir deploymentDir) throws IOException {
        super(new URL[] { deploymentDir.appClasses.toUri().toURL() });
        setDefaultAssertionStatus(true);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> clazz = findLoadedClass(name);
            if (clazz != null) {
                return clazz;
            }

            try {
                clazz = findClass(name);
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, resolve);
            }
        }
    }
}
