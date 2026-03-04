package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Pattern;

public class PatternValidator implements ConstraintValidator<Pattern, CharSequence> {

    private java.util.regex.Pattern pattern;

    @Override
    public void initialize(Pattern constraintAnnotation) {
        Pattern.Flag[] flags = constraintAnnotation.flags();
        int intFlag = 0;
        for (Pattern.Flag flag : flags) {
            intFlag |= flag.getValue();
        }
        pattern = java.util.regex.Pattern.compile(constraintAnnotation.regexp(), intFlag);
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return pattern.matcher(value).matches();
    }
}
