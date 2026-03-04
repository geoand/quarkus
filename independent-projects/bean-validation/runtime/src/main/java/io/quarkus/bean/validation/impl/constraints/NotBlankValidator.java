package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotBlank;

public class NotBlankValidator implements ConstraintValidator<NotBlank, CharSequence> {

    @Override
    public void initialize(NotBlank constraintAnnotation) {
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return !value.toString().trim().isEmpty();
    }
}
