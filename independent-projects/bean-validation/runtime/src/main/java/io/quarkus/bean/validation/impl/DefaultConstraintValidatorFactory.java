package io.quarkus.bean.validation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ValidationException;

import io.quarkus.bean.validation.ValidatorInstantiator;

/**
 * Default {@link ConstraintValidatorFactory} implementation for Quarkus Bean Validation.
 * <p>
 * Uses a build-time generated {@link ValidatorInstantiator} when available,
 * falling back to reflection using the no-arg constructor.
 */
public class DefaultConstraintValidatorFactory implements ConstraintValidatorFactory {

    private final ValidatorInstantiator validatorInstantiator;

    public DefaultConstraintValidatorFactory() {
        this(null);
    }

    public DefaultConstraintValidatorFactory(ValidatorInstantiator validatorInstantiator) {
        this.validatorInstantiator = validatorInstantiator;
    }

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
        if (validatorInstantiator != null) {
            T instance = validatorInstantiator.getInstance(key);
            if (instance != null) {
                return instance;
            }
        }
        try {
            return key.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ValidationException("Unable to instantiate constraint validator: " + key.getName(), e);
        }
    }

    @Override
    public void releaseInstance(ConstraintValidator<?, ?> instance) {
        // no-op
    }
}
