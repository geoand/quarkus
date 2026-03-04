package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotNull;

public class NotNullValidator implements ConstraintValidator<NotNull, Object> {

    @Override
    public void initialize(NotNull constraintAnnotation) {
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        return value != null;
    }
}
