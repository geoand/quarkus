package io.quarkus.bean.validation.test;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests repeatable constraint annotations (both built-in and custom).
 */
class RepeatableConstraintTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(BuiltinRepeatableBean.class, CustomRepeatableBean.class,
                    MustContain.class, MustContain.List.class, MustContain.MustContainValidator.class)
            .build();

    // --- Built-in @Pattern is @Repeatable ---

    static class BuiltinRepeatableBean {
        @Pattern(regexp = ".*[0-9]+.*", message = "must contain a digit")
        @Pattern(regexp = ".*[a-z]+.*", message = "must contain a lowercase letter")
        String value;
    }

    @Test
    void builtinRepeatableBothPass() {
        BuiltinRepeatableBean bean = new BuiltinRepeatableBean();
        bean.value = "abc123";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void builtinRepeatableOneViolation() {
        BuiltinRepeatableBean bean = new BuiltinRepeatableBean();
        bean.value = "abcdef"; // no digit
        Set<ConstraintViolation<BuiltinRepeatableBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must contain a digit");
    }

    @Test
    void builtinRepeatableBothViolations() {
        BuiltinRepeatableBean bean = new BuiltinRepeatableBean();
        bean.value = "INVALID"; // no digit, no lowercase
        Set<ConstraintViolation<BuiltinRepeatableBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("must contain a digit", "must contain a lowercase letter");
    }

    @Test
    void builtinRepeatableNullIsValid() {
        BuiltinRepeatableBean bean = new BuiltinRepeatableBean();
        bean.value = null; // @Pattern considers null valid
        assertThat(validator().validate(bean)).isEmpty();
    }

    // --- Custom repeatable constraint ---

    @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
    @Retention(RUNTIME)
    @Repeatable(MustContain.List.class)
    @Documented
    @Constraint(validatedBy = MustContain.MustContainValidator.class)
    @interface MustContain {
        String value();

        String message() default "must contain '{value}'";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
        @Retention(RUNTIME)
        @Documented
        @interface List {
            MustContain[] value();
        }

        class MustContainValidator implements ConstraintValidator<MustContain, String> {
            private String required;

            @Override
            public void initialize(MustContain annotation) {
                this.required = annotation.value();
            }

            @Override
            public boolean isValid(String value, ConstraintValidatorContext context) {
                if (value == null) {
                    return true;
                }
                return value.contains(required);
            }
        }
    }

    static class CustomRepeatableBean {
        @MustContain("foo")
        @MustContain("bar")
        String value;
    }

    @Test
    void customRepeatableBothPass() {
        CustomRepeatableBean bean = new CustomRepeatableBean();
        bean.value = "foobar";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void customRepeatableOneViolation() {
        CustomRepeatableBean bean = new CustomRepeatableBean();
        bean.value = "foo-only";
        Set<ConstraintViolation<CustomRepeatableBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
    }

    @Test
    void customRepeatableBothViolations() {
        CustomRepeatableBean bean = new CustomRepeatableBean();
        bean.value = "neither";
        Set<ConstraintViolation<CustomRepeatableBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(2);
    }

    @Test
    void customRepeatableNullIsValid() {
        CustomRepeatableBean bean = new CustomRepeatableBean();
        bean.value = null;
        assertThat(validator().validate(bean)).isEmpty();
    }

    private Validator validator() {
        return container.getValidator();
    }
}
