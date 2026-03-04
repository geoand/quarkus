package io.quarkus.bean.validation.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import jakarta.validation.executable.ExecutableValidator;

import io.quarkus.bean.validation.QuarkusValidator;

public class QuarkusExecutableValidator implements ExecutableValidator {

    private final QuarkusValidator validator;

    public QuarkusExecutableValidator(QuarkusValidator validator) {
        this.validator = validator;
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateParameters(T object, Method method,
            Object[] parameterValues, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("Object must not be null");
        }
        if (method == null) {
            throw new IllegalArgumentException("Method must not be null");
        }
        return validator.validateParameters(object, method, parameterValues, groups);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateReturnValue(T object, Method method,
            Object returnValue, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("Object must not be null");
        }
        if (method == null) {
            throw new IllegalArgumentException("Method must not be null");
        }
        return validator.validateReturnValue(object, method, returnValue, groups);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            Constructor<? extends T> constructor, Object[] parameterValues, Class<?>... groups) {
        if (constructor == null) {
            throw new IllegalArgumentException("Constructor must not be null");
        }
        return validator.validateConstructorParameters(constructor, parameterValues, groups);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(
            Constructor<? extends T> constructor, T createdObject, Class<?>... groups) {
        if (constructor == null) {
            throw new IllegalArgumentException("Constructor must not be null");
        }
        if (createdObject == null) {
            throw new IllegalArgumentException("Created object must not be null");
        }
        return validator.validateConstructorReturnValue(constructor, createdObject, groups);
    }

    public <T> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(QuarkusExecutableValidator.class)) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap to " + type);
    }
}
