package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Null;

public class NullValidator implements ConstraintValidator<Null, Object> {

    @Override
    public void initialize(Null constraintAnnotation) {
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        return value == null;
    }
}
