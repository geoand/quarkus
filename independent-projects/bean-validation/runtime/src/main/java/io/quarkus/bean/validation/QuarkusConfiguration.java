package io.quarkus.bean.validation;

import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.validation.BootstrapConfiguration;
import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableType;
import jakarta.validation.spi.BootstrapState;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.valueextraction.ValueExtractor;

import io.quarkus.bean.validation.impl.DefaultClockProvider;
import io.quarkus.bean.validation.impl.DefaultConstraintValidatorFactory;
import io.quarkus.bean.validation.impl.DefaultMessageInterpolator;
import io.quarkus.bean.validation.impl.DefaultParameterNameProvider;
import io.quarkus.bean.validation.impl.DefaultTraversableResolver;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;

/**
 * Quarkus implementation of {@link Configuration} and {@link ConfigurationState}.
 * <p>
 * This configuration always ignores XML configuration, as Quarkus performs
 * constraint discovery at build time. An optional {@link BeanValidationMetadata}
 * can be injected by the build-time processor to supply pre-computed metadata.
 */
public class QuarkusConfiguration implements Configuration<QuarkusConfiguration>, ConfigurationState {

    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private ParameterNameProvider parameterNameProvider;
    private ClockProvider clockProvider;
    private final Set<ValueExtractor<?>> valueExtractors = new HashSet<>();
    private final Map<String, String> properties = new HashMap<>();

    private BeanValidationMetadata beanValidationMetadata;

    public QuarkusConfiguration(BootstrapState bootstrapState) {
        this.messageInterpolator = getDefaultMessageInterpolator();
        this.traversableResolver = getDefaultTraversableResolver();
        this.constraintValidatorFactory = getDefaultConstraintValidatorFactory();
        this.parameterNameProvider = getDefaultParameterNameProvider();
        this.clockProvider = getDefaultClockProvider();
    }

    // ---- Configuration methods ----

    @Override
    public QuarkusConfiguration ignoreXmlConfiguration() {
        // Always ignoring XML; this is a no-op
        return this;
    }

    @Override
    public QuarkusConfiguration messageInterpolator(MessageInterpolator interpolator) {
        this.messageInterpolator = interpolator;
        return this;
    }

    @Override
    public QuarkusConfiguration traversableResolver(TraversableResolver resolver) {
        this.traversableResolver = resolver;
        return this;
    }

    @Override
    public QuarkusConfiguration constraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
        this.constraintValidatorFactory = constraintValidatorFactory;
        return this;
    }

    @Override
    public QuarkusConfiguration parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    @Override
    public QuarkusConfiguration clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public QuarkusConfiguration addValueExtractor(ValueExtractor<?> extractor) {
        this.valueExtractors.add(extractor);
        return this;
    }

    @Override
    public QuarkusConfiguration addMapping(InputStream stream) {
        // No-op: XML constraint mapping is not supported in Quarkus
        return this;
    }

    @Override
    public QuarkusConfiguration addProperty(String name, String value) {
        this.properties.put(name, value);
        return this;
    }

    // ---- Default SPI providers ----

    @Override
    public MessageInterpolator getDefaultMessageInterpolator() {
        return new DefaultMessageInterpolator();
    }

    @Override
    public TraversableResolver getDefaultTraversableResolver() {
        return new DefaultTraversableResolver();
    }

    @Override
    public ConstraintValidatorFactory getDefaultConstraintValidatorFactory() {
        return new DefaultConstraintValidatorFactory();
    }

    @Override
    public ParameterNameProvider getDefaultParameterNameProvider() {
        return new DefaultParameterNameProvider();
    }

    @Override
    public ClockProvider getDefaultClockProvider() {
        return new DefaultClockProvider();
    }

    @Override
    public BootstrapConfiguration getBootstrapConfiguration() {
        return new BootstrapConfiguration() {
            @Override
            public String getDefaultProviderClassName() {
                return QuarkusValidationProvider.class.getName();
            }

            @Override
            public String getConstraintValidatorFactoryClassName() {
                return null;
            }

            @Override
            public String getMessageInterpolatorClassName() {
                return null;
            }

            @Override
            public String getTraversableResolverClassName() {
                return null;
            }

            @Override
            public String getParameterNameProviderClassName() {
                return null;
            }

            @Override
            public String getClockProviderClassName() {
                return null;
            }

            @Override
            public Set<String> getValueExtractorClassNames() {
                return Collections.emptySet();
            }

            @Override
            public Set<String> getConstraintMappingResourcePaths() {
                return Collections.emptySet();
            }

            @Override
            public boolean isExecutableValidationEnabled() {
                return true;
            }

            @Override
            public Set<ExecutableType> getDefaultValidatedExecutableTypes() {
                return EnumSet.of(ExecutableType.CONSTRUCTORS, ExecutableType.NON_GETTER_METHODS);
            }

            @Override
            public Map<String, String> getProperties() {
                return Collections.emptyMap();
            }
        };
    }

    @Override
    public ValidatorFactory buildValidatorFactory() {
        return new QuarkusValidatorFactory(this);
    }

    // ---- ConfigurationState methods ----

    @Override
    public boolean isIgnoreXmlConfiguration() {
        return true;
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    @Override
    public Set<InputStream> getMappingStreams() {
        return Collections.emptySet();
    }

    @Override
    public Set<ValueExtractor<?>> getValueExtractors() {
        return Collections.unmodifiableSet(valueExtractors);
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    @Override
    public ParameterNameProvider getParameterNameProvider() {
        return parameterNameProvider;
    }

    @Override
    public ClockProvider getClockProvider() {
        return clockProvider;
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    // ---- BeanValidationMetadata support ----

    public BeanValidationMetadata getBeanValidationMetadata() {
        return beanValidationMetadata;
    }

    public void setBeanValidationMetadata(BeanValidationMetadata beanValidationMetadata) {
        this.beanValidationMetadata = beanValidationMetadata;
    }
}
