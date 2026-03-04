package io.quarkus.bean.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.BootstrapState;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.spi.ValidationProvider;

import org.jboss.logging.Logger;

import io.quarkus.bean.validation.impl.AnnotationProxy;
import io.quarkus.bean.validation.impl.DefaultMessageInterpolator;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;

/**
 * Quarkus implementation of {@link ValidationProvider}.
 * <p>
 * This provider creates Quarkus-specific configuration and validator factory instances,
 * optimized for build-time processing and native image compatibility.
 * <p>
 * Also serves as the single holder of all runtime state initialized at {@code STATIC_INIT}:
 * metadata, constraint validator factory, locale, annotation literals, and property accessors.
 * Call {@link #configure} before bootstrap so that {@code Validation.buildDefaultValidatorFactory()}
 * returns a fully configured validator.
 */
public class QuarkusValidationProvider implements ValidationProvider<QuarkusConfiguration> {

    private static final Logger LOG = Logger.getLogger(QuarkusValidationProvider.class);

    private static volatile RuntimeConfig config;

    record RuntimeConfig(BeanValidationMetadata metadata, ConstraintValidatorFactory constraintValidatorFactory,
            Locale defaultLocale, Map<String, Constructor<?>> annotationLiterals,
            Map<String, BeanPropertyAccessor> accessors, boolean extensionMode) {
    }

    // ---- Configuration API ----

    /**
     * Configures the provider with all runtime state. Called by the Quarkus recorder
     * at {@code STATIC_INIT}. All state is stored as a single atomic snapshot.
     *
     * @param metadata pre-built validation metadata (may be {@code null})
     * @param factory constraint validator factory (e.g. Arc-aware), may be {@code null}
     * @param locale default locale for message interpolation, may be {@code null}
     * @param annotationToLiteralMap annotation FQCN to generated literal class FQCN, may be {@code null}
     * @param beanToAccessorMap bean FQCN to generated accessor class FQCN, may be {@code null}
     */
    public static void configure(BeanValidationMetadata metadata,
            ConstraintValidatorFactory factory,
            Locale locale,
            Map<String, String> annotationToLiteralMap,
            Map<String, String> beanToAccessorMap) {
        config = new RuntimeConfig(metadata, factory, locale,
                resolveAnnotationLiterals(annotationToLiteralMap),
                resolvePropertyAccessors(beanToAccessorMap),
                true);
    }

    /**
     * Configures the provider with metadata only (no annotation literals or property accessors).
     * Used by the Arquillian container during TCK test deployment.
     */
    public static void configure(BeanValidationMetadata metadata,
            ConstraintValidatorFactory factory,
            Locale locale) {
        config = new RuntimeConfig(metadata, factory, locale, Map.of(), Map.of(), false);
    }

    public static void clearConfiguration() {
        config = null;
    }

    // ---- Annotation literal creation ----

    /**
     * Creates an annotation instance for the given type and attributes.
     * Uses the generated literal class if available, otherwise falls back to {@link AnnotationProxy}.
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A createAnnotationLiteral(Class<A> type, Map<String, Object> attrs) {
        RuntimeConfig c = config;
        if (c != null) {
            Constructor<?> ctor = c.annotationLiterals.get(type.getName());
            if (ctor != null) {
                try {
                    return (A) ctor.newInstance(type, attrs);
                } catch (Exception e) {
                    throw new ValidationException(
                            "Failed to create annotation literal for " + type.getName(), e);
                }
            }
            if (c.extensionMode) {
                throw new ValidationException(
                        "No generated annotation literal found for " + type.getName()
                                + ". Ensure the annotation is indexed at build time.");
            }
        }
        // Fallback for non-Quarkus contexts (e.g. Arquillian/TCK)
        return AnnotationProxy.create(type, attrs);
    }

    // ---- Property accessor lookup ----

    public static BeanPropertyAccessor getAccessor(String beanClassName) {
        RuntimeConfig c = config;
        return c != null ? c.accessors.get(beanClassName) : null;
    }

    /**
     * Returns {@code true} if the provider has been configured by the Quarkus extension
     * (i.e. build-time bytecode transformations and accessor generation have been performed).
     * When {@code true}, reflection fallbacks should not be used.
     * <p>
     * Returns {@code false} when configured by the Arquillian/TCK container, which provides
     * metadata but does not perform bytecode transformations or generate accessors.
     */
    public static boolean isExtensionMode() {
        RuntimeConfig c = config;
        return c != null && c.extensionMode;
    }

    // ---- ValidationProvider implementation ----

    @Override
    public QuarkusConfiguration createSpecializedConfiguration(BootstrapState state) {
        QuarkusConfiguration cfg = new QuarkusConfiguration(state);
        RuntimeConfig c = config;
        if (c != null) {
            if (c.metadata != null) {
                cfg.setBeanValidationMetadata(c.metadata);
            }
            if (c.constraintValidatorFactory != null) {
                cfg.constraintValidatorFactory(c.constraintValidatorFactory);
            }
            if (c.defaultLocale != null) {
                cfg.messageInterpolator(new DefaultMessageInterpolator(c.defaultLocale));
            }
        }
        return cfg;
    }

    @Override
    public Configuration<?> createGenericConfiguration(BootstrapState state) {
        return createSpecializedConfiguration(state);
    }

    @Override
    public ValidatorFactory buildValidatorFactory(ConfigurationState configurationState) {
        return new QuarkusValidatorFactory(configurationState);
    }

    // ---- Validator instantiator ----

    public static ValidatorInstantiator resolveValidatorInstantiator(String className) {
        if (className == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className, true,
                    Thread.currentThread().getContextClassLoader());
            return (ValidatorInstantiator) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            LOG.error("Failed to load validator instantiator: " + e.getMessage());
            throw new ValidationException(e);
        }
    }

    // ---- Resolution helpers ----

    private static Map<String, Constructor<?>> resolveAnnotationLiterals(Map<String, String> annotationToLiteralMap) {
        if (annotationToLiteralMap == null || annotationToLiteralMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Constructor<?>> map = new HashMap<>();
        for (Map.Entry<String, String> entry : annotationToLiteralMap.entrySet()) {
            try {
                Class<?> literalClass = Class.forName(entry.getValue(), true,
                        Thread.currentThread().getContextClassLoader());
                Constructor<?> ctor = literalClass.getConstructor(Class.class, Map.class);
                map.put(entry.getKey(), ctor);
            } catch (Exception | NoClassDefFoundError e) {
                // NoClassDefFoundError can occur when the generated literal implements an annotation
                // from a test archive that isn't available in the current classloader.
                LOG.error("Failed to load annotation literal class for "
                        + entry.getKey() + ": " + e.getMessage());
                throw new ValidationException(e);
            }
        }
        return map;
    }

    private static Map<String, BeanPropertyAccessor> resolvePropertyAccessors(Map<String, String> beanToAccessorMap) {
        if (beanToAccessorMap == null || beanToAccessorMap.isEmpty()) {
            return Map.of();
        }
        Map<String, BeanPropertyAccessor> map = new HashMap<>();
        for (Map.Entry<String, String> entry : beanToAccessorMap.entrySet()) {
            try {
                Class<?> accessorClass = Class.forName(entry.getValue(), true,
                        Thread.currentThread().getContextClassLoader());
                BeanPropertyAccessor accessor = (BeanPropertyAccessor) accessorClass.getConstructor().newInstance();
                map.put(entry.getKey(), accessor);
            } catch (Exception e) {
                LOG.error("Failed to load property accessor for "
                        + entry.getKey() + ": " + e.getMessage());
                throw new ValidationException(e);
            }
        }
        return map;
    }
}
