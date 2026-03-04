package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.AssertTrue;

public class AssertTrueValidator implements ConstraintValidator<AssertTrue, Boolean> {

    @Override
    public void initialize(AssertTrue constraintAnnotation) {
    }

    @Override
    public boolean isValid(Boolean value, ConstraintValidatorContext context) {
        return value == null || value;
    }
}
