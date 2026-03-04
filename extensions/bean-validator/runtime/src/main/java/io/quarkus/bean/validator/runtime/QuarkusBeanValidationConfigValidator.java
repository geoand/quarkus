package io.quarkus.bean.validator.runtime;

import java.util.Set;

import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import io.quarkus.bean.validation.QuarkusConfiguration;
import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.impl.DefaultConstraintValidatorFactory;
import io.quarkus.bean.validation.impl.DefaultTraversableResolver;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedBeanMetadata;

/**
 * Config validator that uses build-time metadata constructed via Gizmo bytecodes,
 * eliminating the need for runtime reflection to discover constraints.
 * <p>
 * The only remaining reflection is {@code Class.getInterfaces()} per generated
 * config class (called once at initialization) to map generated implementation
 * class names to interface names in the metadata.
 */
public class QuarkusBeanValidationConfigValidator implements io.smallrye.config.validator.BeanValidationConfigValidator {

    private static volatile Validator validator;

    /**
     * @param metadata pre-built validation metadata from build-time Jandex processing
     * @param classesToBeValidated generated config mapping implementation classes
     */
    public QuarkusBeanValidationConfigValidator(BeanValidationMetadata metadata, Set<Class<?>> classesToBeValidated) {
        // Map generated implementation class names to interface metadata.
        // SmallRye Config passes generated implementation objects to the validator,
        // but the metadata is keyed by the original interface name.
        for (Class<?> genClass : classesToBeValidated) {
            if (metadata.getBean(genClass.getName()) != null) {
                continue; // Already has a direct entry
            }
            for (Class<?> iface : genClass.getInterfaces()) {
                ConstrainedBeanMetadata ifaceMeta = metadata.getBean(iface.getName());
                if (ifaceMeta != null) {
                    metadata.beans().put(genClass.getName(), ifaceMeta);
                    break;
                }
            }
        }

        QuarkusValidationProvider provider = new QuarkusValidationProvider();
        QuarkusConfiguration configuration = provider.createSpecializedConfiguration(null);
        configuration.setBeanValidationMetadata(metadata);
        configuration
                .constraintValidatorFactory(new DefaultConstraintValidatorFactory())
                .traversableResolver(new DefaultTraversableResolver());

        ValidatorFactory validatorFactory = configuration.buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Override
    public Validator getValidator() {
        return validator;
    }
}
