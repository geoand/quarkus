package io.quarkus.bean.validation.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;

public class QuarkusConstraintViolation<T> implements ConstraintViolation<T> {

    private final String messageTemplate;
    private final String message;
    private final T rootBean;
    private final Class<T> rootBeanClass;
    private final Object leafBean;
    private final Object invalidValue;
    private final Path propertyPath;
    private final ConstraintDescriptor<?> constraintDescriptor;
    private final Object[] executableParameters;
    private final Object executableReturnValue;

    public QuarkusConstraintViolation(String messageTemplate,
            String message,
            T rootBean,
            Class<T> rootBeanClass,
            Object leafBean,
            Object invalidValue,
            Path propertyPath,
            ConstraintDescriptor<?> constraintDescriptor,
            Object[] executableParameters,
            Object executableReturnValue) {
        this.messageTemplate = messageTemplate;
        this.message = message;
        this.rootBean = rootBean;
        this.rootBeanClass = rootBeanClass;
        this.leafBean = leafBean;
        this.invalidValue = invalidValue;
        this.propertyPath = propertyPath;
        this.constraintDescriptor = constraintDescriptor;
        this.executableParameters = executableParameters;
        this.executableReturnValue = executableReturnValue;
    }

    @Override
    public String getMessageTemplate() {
        return messageTemplate;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public T getRootBean() {
        return rootBean;
    }

    @Override
    public Class<T> getRootBeanClass() {
        return rootBeanClass;
    }

    @Override
    public Object getLeafBean() {
        return leafBean;
    }

    @Override
    public Object getInvalidValue() {
        return invalidValue;
    }

    @Override
    public Path getPropertyPath() {
        return propertyPath;
    }

    @Override
    public ConstraintDescriptor<?> getConstraintDescriptor() {
        return constraintDescriptor;
    }

    @Override
    public Object[] getExecutableParameters() {
        return executableParameters;
    }

    @Override
    public Object getExecutableReturnValue() {
        return executableReturnValue;
    }

    @Override
    public <U> U unwrap(Class<U> type) {
        if (type.isAssignableFrom(getClass())) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap " + getClass().getName() + " to " + type.getName());
    }

    @Override
    public String toString() {
        return "QuarkusConstraintViolation{"
                + "path=" + propertyPath
                + ", message='" + message + '\''
                + ", messageTemplate='" + messageTemplate + '\''
                + ", rootBeanClass=" + (rootBeanClass != null ? rootBeanClass.getName() : "null")
                + ", invalidValue=" + invalidValue
                + '}';
    }
}
