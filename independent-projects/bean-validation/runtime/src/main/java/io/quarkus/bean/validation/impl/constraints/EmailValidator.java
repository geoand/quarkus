package io.quarkus.bean.validation.impl.constraints;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Email;

public class EmailValidator implements ConstraintValidator<Email, CharSequence> {

    private static final String LOCAL_PART = "[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+";
    private static final String DOMAIN_PART = "[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?)*";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^" + LOCAL_PART + "@" + DOMAIN_PART + "$");

    private Pattern additionalPattern;

    @Override
    public void initialize(Email constraintAnnotation) {
        String regexp = constraintAnnotation.regexp();
        jakarta.validation.constraints.Pattern.Flag[] flags = constraintAnnotation.flags();

        // Only compile additional pattern if a custom regexp was specified
        if (!".*".equals(regexp)) {
            int intFlag = 0;
            for (jakarta.validation.constraints.Pattern.Flag flag : flags) {
                intFlag |= flag.getValue();
            }
            additionalPattern = Pattern.compile(regexp, intFlag);
        }
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String email = value.toString();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return false;
        }
        return additionalPattern == null || additionalPattern.matcher(email).matches();
    }
}
