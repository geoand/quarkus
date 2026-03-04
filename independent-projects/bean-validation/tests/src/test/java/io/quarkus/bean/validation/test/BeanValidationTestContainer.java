package io.quarkus.bean.validation.test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.quarkus.bean.validation.QuarkusConfiguration;
import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.processor.BeanValidationProcessor;

/**
 * JUnit 5 extension that orchestrates:
 * 1. Jandex indexing of test classes
 * 2. Build-time processing to produce metadata
 * 3. Configuring a ValidatorFactory with the metadata
 *
 * Usage:
 *
 * <pre>
 * &#64;RegisterExtension
 * static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
 *         .beanClasses(MyBean.class, OtherBean.class)
 *         .build();
 * </pre>
 */
public class BeanValidationTestContainer implements BeforeEachCallback, AfterEachCallback {

    private final List<Class<?>> beanClasses;
    private ValidatorFactory validatorFactory;

    private BeanValidationTestContainer(Builder builder) {
        this.beanClasses = new ArrayList<>(builder.beanClasses);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // 1. Index all bean classes with Jandex
        Indexer indexer = new Indexer();
        Set<String> indexed = new HashSet<>();

        // Index the Jakarta Validation API so that built-in constraint annotations
        // are available in the Jandex index (no reflection fallbacks needed)
        indexJakartaValidationApi(indexer, indexed);

        for (Class<?> clazz : beanClasses) {
            indexClass(indexer, clazz, indexed);
            // Also discover and index custom constraint annotation classes
            // used on fields, methods, and parameters of bean classes
            discoverAndIndexAnnotations(indexer, clazz, indexed);
        }
        Index index = indexer.complete();

        // 2. Process with Jandex to build metadata
        BeanValidationProcessor processor = new BeanValidationProcessor();
        BeanValidationMetadata metadata = processor.process(index);

        // 3. Create ValidatorFactory with the metadata
        QuarkusConfiguration configuration = (QuarkusConfiguration) Validation
                .byProvider(QuarkusValidationProvider.class)
                .configure();
        configuration.setBeanValidationMetadata(metadata);
        validatorFactory = configuration.buildValidatorFactory();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (validatorFactory != null) {
            validatorFactory.close();
            validatorFactory = null;
        }
    }

    public ValidatorFactory getValidatorFactory() {
        return validatorFactory;
    }

    public Validator getValidator() {
        return validatorFactory.getValidator();
    }

    private void indexClass(Indexer indexer, Class<?> clazz, Set<String> indexed) throws IOException {
        String className = clazz.getName().replace('.', '/') + ".class";
        if (!indexed.add(className)) {
            return; // already indexed
        }
        try (InputStream stream = clazz.getClassLoader().getResourceAsStream(className)) {
            if (stream != null) {
                indexer.index(stream);
            }
        }
    }

    /**
     * Indexes all classes from the jakarta.validation-api JAR so that built-in
     * constraint annotations and BV meta-annotations are available via Jandex.
     */
    private void indexJakartaValidationApi(Indexer indexer, Set<String> indexed) throws IOException {
        // Use a known class from the JAR to locate it
        String jarMarker = jakarta.validation.Validator.class.getName().replace('.', '/') + ".class";
        java.net.URL url = jakarta.validation.Validator.class.getClassLoader().getResource(jarMarker);
        if (url == null) {
            return;
        }
        String urlStr = url.toString();
        if (urlStr.startsWith("jar:")) {
            // Extract JAR path: "jar:file:/path/to.jar!/..."
            String jarPath = urlStr.substring(4, urlStr.indexOf('!'));
            try (java.util.jar.JarInputStream jarStream = new java.util.jar.JarInputStream(
                    new java.net.URI(jarPath).toURL().openStream())) {
                java.util.jar.JarEntry entry;
                while ((entry = jarStream.getNextJarEntry()) != null) {
                    if (entry.getName().endsWith(".class")) {
                        if (indexed.add(entry.getName())) {
                            indexer.index(jarStream);
                        }
                    }
                }
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Invalid JAR URI: " + jarPath, e);
            }
        }
    }

    /**
     * Discovers custom constraint annotation classes used on the fields, methods,
     * and parameters of the given class, and indexes them (including their validator classes).
     */
    private void discoverAndIndexAnnotations(Indexer indexer, Class<?> clazz, Set<String> indexed) throws IOException {
        // Check field annotations
        for (Field field : clazz.getDeclaredFields()) {
            for (Annotation annotation : field.getAnnotations()) {
                indexConstraintAnnotation(indexer, annotation.annotationType(), indexed);
            }
        }
        // Check method annotations
        for (Method method : clazz.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                indexConstraintAnnotation(indexer, annotation.annotationType(), indexed);
            }
            for (Parameter param : method.getParameters()) {
                for (Annotation annotation : param.getAnnotations()) {
                    indexConstraintAnnotation(indexer, annotation.annotationType(), indexed);
                }
            }
        }
    }

    /**
     * If the given annotation type is annotated with {@code @Constraint}, indexes it
     * and also indexes the validator classes referenced in its {@code validatedBy} attribute.
     */
    private void indexConstraintAnnotation(Indexer indexer, Class<? extends Annotation> annotationType,
            Set<String> indexed) throws IOException {
        Constraint constraint = annotationType.getAnnotation(Constraint.class);
        if (constraint == null) {
            return;
        }
        // Index the annotation class itself
        indexClass(indexer, annotationType, indexed);
        // Index the validator classes
        for (Class<?> validatorClass : constraint.validatedBy()) {
            indexClass(indexer, validatorClass, indexed);
        }
    }

    public static class Builder {
        private final List<Class<?>> beanClasses = new ArrayList<>();

        public Builder beanClasses(Class<?>... classes) {
            for (Class<?> clazz : classes) {
                beanClasses.add(clazz);
            }
            return this;
        }

        public BeanValidationTestContainer build() {
            return new BeanValidationTestContainer(this);
        }
    }
}
