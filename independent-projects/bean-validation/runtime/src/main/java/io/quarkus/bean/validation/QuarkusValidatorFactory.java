package io.quarkus.bean.validation;

import java.util.Set;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.valueextraction.ValueExtractor;

import io.quarkus.bean.validation.impl.QuarkusValidatorContext;
import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;

public class QuarkusValidatorFactory implements ValidatorFactory {

    private final BeanValidationMetadata metadata;
    private final MessageInterpolator messageInterpolator;
    private final TraversableResolver traversableResolver;
    private final ConstraintValidatorFactory constraintValidatorFactory;
    private final ParameterNameProvider parameterNameProvider;
    private final ClockProvider clockProvider;
    private final Set<ValueExtractor<?>> valueExtractors;

    private volatile QuarkusValidator validator;

    public QuarkusValidatorFactory(ConfigurationState configurationState) {
        this.metadata = (configurationState instanceof QuarkusConfiguration)
                ? ((QuarkusConfiguration) configurationState).getBeanValidationMetadata()
                : null;
        this.messageInterpolator = configurationState.getMessageInterpolator();
        this.traversableResolver = configurationState.getTraversableResolver();
        this.constraintValidatorFactory = configurationState.getConstraintValidatorFactory();
        this.parameterNameProvider = configurationState.getParameterNameProvider();
        this.clockProvider = configurationState.getClockProvider();
        this.valueExtractors = configurationState.getValueExtractors();
    }

    @Override
    public Validator getValidator() {
        QuarkusValidator result = this.validator;
        if (result == null) {
            synchronized (this) {
                result = this.validator;
                if (result == null) {
                    result = new QuarkusValidator(
                            metadata,
                            messageInterpolator,
                            traversableResolver,
                            constraintValidatorFactory,
                            parameterNameProvider,
                            clockProvider,
                            valueExtractors);
                    this.validator = result;
                }
            }
        }
        return result;
    }

    @Override
    public ValidatorContext usingContext() {
        return new QuarkusValidatorContext(this);
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
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
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(QuarkusValidatorFactory.class)) {
            return (T) this;
        }
        throw new ValidationException("Type " + type.getName() + " is not supported for unwrapping");
    }

    @Override
    public void close() {
        // no-op
    }

    public BeanValidationMetadata getMetadata() {
        return metadata;
    }
}
