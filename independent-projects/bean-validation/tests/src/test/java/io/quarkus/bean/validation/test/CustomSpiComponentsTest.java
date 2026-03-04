package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.QuarkusConfiguration;
import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.processor.BeanValidationProcessor;

/**
 * Tests that custom SPI components (ClockProvider, MessageInterpolator,
 * TraversableResolver, ParameterNameProvider, ConstraintValidatorFactory)
 * are properly used by the validator.
 */
class CustomSpiComponentsTest {

    private ValidatorFactory validatorFactory;

    @AfterEach
    void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    // --- Custom ClockProvider ---

    static class TemporalBean {
        @Past
        LocalDate pastDate;

        @Future
        LocalDate futureDate;
    }

    @Test
    void customClockProviderAffectsTemporalValidation() throws Exception {
        // Use a clock fixed in the year 2000
        Clock fixedClock = Clock.fixed(
                Instant.parse("2000-06-15T00:00:00Z"),
                ZoneId.of("UTC"));

        validatorFactory = buildFactory(TemporalBean.class, config -> {
            config.clockProvider(() -> fixedClock);
        });

        TemporalBean bean = new TemporalBean();
        bean.pastDate = LocalDate.of(1999, 1, 1); // before 2000 => past => valid
        bean.futureDate = LocalDate.of(2001, 1, 1); // after 2000 => future => valid
        assertThat(validatorFactory.getValidator().validate(bean)).isEmpty();

        // Now a date in 2025 should be "future" relative to 2000
        bean.pastDate = LocalDate.of(2025, 1, 1); // after 2000 => NOT past
        Set<ConstraintViolation<TemporalBean>> violations = validatorFactory.getValidator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("pastDate");
    }

    @Test
    void factoryExposesCustomClockProvider() throws Exception {
        ClockProvider custom = () -> Clock.systemUTC();
        validatorFactory = buildFactory(TemporalBean.class, config -> {
            config.clockProvider(custom);
        });
        assertThat(validatorFactory.getClockProvider()).isSameAs(custom);
    }

    // --- Custom MessageInterpolator ---

    static class MessageBean {
        @NotNull
        String value;
    }

    @Test
    void customMessageInterpolator() throws Exception {
        MessageInterpolator customInterpolator = new MessageInterpolator() {
            @Override
            public String interpolate(String messageTemplate, Context context) {
                return "CUSTOM: " + messageTemplate;
            }

            @Override
            public String interpolate(String messageTemplate, Context context, java.util.Locale locale) {
                return "CUSTOM: " + messageTemplate;
            }
        };

        validatorFactory = buildFactory(MessageBean.class, config -> {
            config.messageInterpolator(customInterpolator);
        });

        MessageBean bean = new MessageBean();
        bean.value = null;
        Set<ConstraintViolation<MessageBean>> violations = validatorFactory.getValidator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).startsWith("CUSTOM: ");
    }

    @Test
    void factoryExposesCustomMessageInterpolator() throws Exception {
        MessageInterpolator custom = new MessageInterpolator() {
            @Override
            public String interpolate(String messageTemplate, Context context) {
                return messageTemplate;
            }

            @Override
            public String interpolate(String messageTemplate, Context context, java.util.Locale locale) {
                return messageTemplate;
            }
        };
        validatorFactory = buildFactory(MessageBean.class, config -> {
            config.messageInterpolator(custom);
        });
        assertThat(validatorFactory.getMessageInterpolator()).isSameAs(custom);
    }

    // --- Custom TraversableResolver ---

    @Test
    void factoryExposesCustomTraversableResolver() throws Exception {
        TraversableResolver custom = new TraversableResolver() {
            @Override
            public boolean isReachable(Object obj, Path.Node node, Class<?> rootBeanType,
                    Path path, ElementType elementType) {
                return true;
            }

            @Override
            public boolean isCascadable(Object obj, Path.Node node, Class<?> rootBeanType,
                    Path path, ElementType elementType) {
                return true;
            }
        };
        validatorFactory = buildFactory(MessageBean.class, config -> {
            config.traversableResolver(custom);
        });
        assertThat(validatorFactory.getTraversableResolver()).isSameAs(custom);
    }

    // --- Custom ParameterNameProvider ---

    static class ParamNameBean {
        public void greet(@NotNull String name) {
        }
    }

    @Test
    void customParameterNameProvider() throws Exception {
        ParameterNameProvider customProvider = new ParameterNameProvider() {
            @Override
            public List<String> getParameterNames(Constructor<?> constructor) {
                return Arrays.asList("customParam");
            }

            @Override
            public List<String> getParameterNames(Method method) {
                return Arrays.asList("customName");
            }
        };

        validatorFactory = buildFactory(ParamNameBean.class, config -> {
            config.parameterNameProvider(customProvider);
        });

        ParamNameBean bean = new ParamNameBean();
        Method method = ParamNameBean.class.getMethod("greet", String.class);
        Set<ConstraintViolation<ParamNameBean>> violations = validatorFactory.getValidator()
                .forExecutables().validateParameters(bean, method, new Object[] { null });
        assertThat(violations).hasSize(1);
        // The custom parameter name provider should provide "customName"
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("greet.customName");
    }

    @Test
    void factoryExposesCustomParameterNameProvider() throws Exception {
        ParameterNameProvider custom = new ParameterNameProvider() {
            @Override
            public List<String> getParameterNames(Constructor<?> c) {
                return List.of();
            }

            @Override
            public List<String> getParameterNames(Method m) {
                return List.of();
            }
        };
        validatorFactory = buildFactory(MessageBean.class, config -> {
            config.parameterNameProvider(custom);
        });
        assertThat(validatorFactory.getParameterNameProvider()).isSameAs(custom);
    }

    // --- Custom ConstraintValidatorFactory ---

    @Test
    void factoryExposesCustomConstraintValidatorFactory() throws Exception {
        ConstraintValidatorFactory custom = new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                try {
                    return key.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new ValidationException(e);
                }
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instance) {
            }
        };
        validatorFactory = buildFactory(MessageBean.class, config -> {
            config.constraintValidatorFactory(custom);
        });
        assertThat(validatorFactory.getConstraintValidatorFactory()).isSameAs(custom);
    }

    // --- Helper to build a ValidatorFactory with custom configuration ---

    @FunctionalInterface
    interface ConfigCustomizer {
        void customize(QuarkusConfiguration config);
    }

    private ValidatorFactory buildFactory(Class<?> beanClass, ConfigCustomizer customizer) throws Exception {
        Indexer indexer = new Indexer();
        Set<String> indexed = new java.util.HashSet<>();

        // Index Jakarta Validation API
        String jarMarker = jakarta.validation.Validator.class.getName().replace('.', '/') + ".class";
        java.net.URL url = jakarta.validation.Validator.class.getClassLoader().getResource(jarMarker);
        if (url != null) {
            String urlStr = url.toString();
            if (urlStr.startsWith("jar:")) {
                String jarPath = urlStr.substring(4, urlStr.indexOf('!'));
                try (java.util.jar.JarInputStream jarStream = new java.util.jar.JarInputStream(
                        new java.net.URI(jarPath).toURL().openStream())) {
                    java.util.jar.JarEntry entry;
                    while ((entry = jarStream.getNextJarEntry()) != null) {
                        if (entry.getName().endsWith(".class") && indexed.add(entry.getName())) {
                            indexer.index(jarStream);
                        }
                    }
                }
            }
        }

        // Index the bean class
        String className = beanClass.getName().replace('.', '/') + ".class";
        if (indexed.add(className)) {
            try (var stream = beanClass.getClassLoader().getResourceAsStream(className)) {
                if (stream != null) {
                    indexer.index(stream);
                }
            }
        }

        Index index = indexer.complete();
        BeanValidationProcessor processor = new BeanValidationProcessor();
        BeanValidationMetadata metadata = processor.process(index);

        QuarkusConfiguration config = (QuarkusConfiguration) Validation
                .byProvider(QuarkusValidationProvider.class)
                .configure();
        config.setBeanValidationMetadata(metadata);
        customizer.customize(config);
        return config.buildValidatorFactory();
    }
}
