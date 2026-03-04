package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.AssertFalse;

public class AssertFalseValidator implements ConstraintValidator<AssertFalse, Boolean> {

    @Override
    public void initialize(AssertFalse constraintAnnotation) {
    }

    @Override
    public boolean isValid(Boolean value, ConstraintValidatorContext context) {
        return value == null || !value;
    }
}
