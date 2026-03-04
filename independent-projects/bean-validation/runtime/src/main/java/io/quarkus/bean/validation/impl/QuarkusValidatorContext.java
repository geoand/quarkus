package io.quarkus.bean.validation.impl;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.valueextraction.ValueExtractor;

import io.quarkus.bean.validation.QuarkusValidator;
import io.quarkus.bean.validation.QuarkusValidatorFactory;

/**
 * Quarkus implementation of {@link ValidatorContext}.
 * <p>
 * Allows overriding individual SPI implementations before creating a new
 * {@link QuarkusValidator} instance. Falls back to the factory defaults
 * for any SPI that is not overridden.
 */
public class QuarkusValidatorContext implements ValidatorContext {

    private final QuarkusValidatorFactory factory;

    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private ParameterNameProvider parameterNameProvider;
    private ClockProvider clockProvider;
    private final Set<ValueExtractor<?>> valueExtractors = new HashSet<>();

    public QuarkusValidatorContext(QuarkusValidatorFactory factory) {
        this.factory = factory;
    }

    @Override
    public ValidatorContext messageInterpolator(MessageInterpolator messageInterpolator) {
        this.messageInterpolator = messageInterpolator;
        return this;
    }

    @Override
    public ValidatorContext traversableResolver(TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
        return this;
    }

    @Override
    public ValidatorContext constraintValidatorFactory(ConstraintValidatorFactory factory) {
        this.constraintValidatorFactory = factory;
        return this;
    }

    @Override
    public ValidatorContext parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    @Override
    public ValidatorContext clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public ValidatorContext addValueExtractor(ValueExtractor<?> extractor) {
        this.valueExtractors.add(extractor);
        return this;
    }

    @Override
    public Validator getValidator() {
        return new QuarkusValidator(
                factory.getMetadata(),
                messageInterpolator != null ? messageInterpolator : factory.getMessageInterpolator(),
                traversableResolver != null ? traversableResolver : factory.getTraversableResolver(),
                constraintValidatorFactory != null ? constraintValidatorFactory : factory.getConstraintValidatorFactory(),
                parameterNameProvider != null ? parameterNameProvider : factory.getParameterNameProvider(),
                clockProvider != null ? clockProvider : factory.getClockProvider(),
                valueExtractors);
    }
}
