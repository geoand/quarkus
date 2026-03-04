package io.quarkus.bean.validator.runtime;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.bean.validation.QuarkusConfiguration;
import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.runtime.LocalesBuildTimeConfig;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class BeanValidatorRecorder {

    public Function<SyntheticCreationalContext<ValidatorFactory>, ValidatorFactory> createValidatorFactory(
            BeanValidationMetadata metadata, List<String> valueExtractorClassNames,
            LocalesBuildTimeConfig localesBuildTimeConfig,
            Map<String, String> annotationToLiteralMap,
            Map<String, String> beanToAccessorMap,
            String validatorInstantiatorClassName) {

        // Configure the provider with all runtime state so that
        // Validation.buildDefaultValidatorFactory() works even before the CDI bean is resolved
        ArcConstraintValidatorFactory arcFactory = new ArcConstraintValidatorFactory(
                QuarkusValidationProvider.resolveValidatorInstantiator(validatorInstantiatorClassName));
        Locale configuredLocale = localesBuildTimeConfig.defaultLocale().orElse(null);
        QuarkusValidationProvider.configure(metadata, arcFactory, configuredLocale,
                annotationToLiteralMap, beanToAccessorMap);

        return new Function<>() {
            @Override
            public ValidatorFactory apply(SyntheticCreationalContext<ValidatorFactory> context) {
                QuarkusValidationProvider provider = new QuarkusValidationProvider();
                QuarkusConfiguration configuration = provider.createSpecializedConfiguration(null);

                // Register custom value extractors from Arc
                if (valueExtractorClassNames != null) {
                    for (String className : valueExtractorClassNames) {
                        try {
                            Class<?> extractorClass = Class.forName(className, true,
                                    Thread.currentThread().getContextClassLoader());
                            InstanceHandle<?> handle = io.quarkus.arc.Arc.container()
                                    .instance(extractorClass);
                            if (handle.isAvailable()) {
                                configuration.addValueExtractor(
                                        (jakarta.validation.valueextraction.ValueExtractor<?>) handle.get());
                            }
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                return configuration.buildValidatorFactory();
            }
        };
    }

    public Function<SyntheticCreationalContext<Validator>, Validator> createValidator() {
        return new Function<>() {
            @Override
            public Validator apply(SyntheticCreationalContext<Validator> context) {
                return context.getInjectedReference(ValidatorFactory.class).getValidator();
            }
        };
    }
}
