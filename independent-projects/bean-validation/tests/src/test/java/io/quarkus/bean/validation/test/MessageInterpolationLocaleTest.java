package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests message interpolation features:
 * - Constraint attribute interpolation in messages ({min}, {max}, etc.)
 * - Custom message templates
 * - Default messages for built-in constraints
 */
class MessageInterpolationLocaleTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(InterpolationBean.class, CustomMessageBean.class)
            .build();

    static class InterpolationBean {
        @Size(min = 3, max = 10)
        String sized;

        @Min(5)
        int min;

        @Max(100)
        int max;

        @NotNull
        String required;
    }

    @Test
    void sizeMessageContainsMinAndMax() {
        InterpolationBean bean = new InterpolationBean();
        bean.sized = "x"; // too short
        bean.min = 10;
        bean.max = 50;
        bean.required = "ok";

        Set<ConstraintViolation<InterpolationBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        String message = violations.iterator().next().getMessage();
        // Default message should mention the bounds
        assertThat(message).contains("3").contains("10");
    }

    @Test
    void minMessageContainsValue() {
        InterpolationBean bean = new InterpolationBean();
        bean.sized = "hello";
        bean.min = 1; // below min of 5
        bean.max = 50;
        bean.required = "ok";

        Set<ConstraintViolation<InterpolationBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        String message = violations.iterator().next().getMessage();
        // Default message should reference the min value
        assertThat(message).contains("5");
    }

    @Test
    void maxMessageContainsValue() {
        InterpolationBean bean = new InterpolationBean();
        bean.sized = "hello";
        bean.min = 10;
        bean.max = 200; // above max of 100
        bean.required = "ok";

        Set<ConstraintViolation<InterpolationBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        String message = violations.iterator().next().getMessage();
        // Default message should reference the max value
        assertThat(message).contains("100");
    }

    // --- Custom message templates ---

    static class CustomMessageBean {
        @NotNull(message = "The field 'name' is required")
        String name;

        @Size(min = 2, max = 5, message = "Length must be between {min} and {max}")
        String code;

        @Min(value = 18, message = "Must be at least {value} years old")
        int age;
    }

    @Test
    void customLiteralMessage() {
        CustomMessageBean bean = new CustomMessageBean();
        bean.name = null;
        bean.code = "AB";
        bean.age = 20;

        Set<ConstraintViolation<CustomMessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("The field 'name' is required");
    }

    @Test
    void customMessageWithAttributeInterpolation() {
        CustomMessageBean bean = new CustomMessageBean();
        bean.name = "ok";
        bean.code = "TOOLONG"; // exceeds max=5
        bean.age = 20;

        Set<ConstraintViolation<CustomMessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Length must be between 2 and 5");
    }

    @Test
    void customMessageWithValueInterpolation() {
        CustomMessageBean bean = new CustomMessageBean();
        bean.name = "ok";
        bean.code = "AB";
        bean.age = 10; // below 18

        Set<ConstraintViolation<CustomMessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Must be at least 18 years old");
    }

    @Test
    void violationContainsCorrectMetadata() {
        CustomMessageBean bean = new CustomMessageBean();
        bean.name = null;
        bean.code = "AB";
        bean.age = 20;

        Set<ConstraintViolation<CustomMessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        ConstraintViolation<CustomMessageBean> v = violations.iterator().next();
        assertThat(v.getMessageTemplate()).isEqualTo("The field 'name' is required");
        assertThat(v.getInvalidValue()).isNull();
        assertThat(v.getPropertyPath().toString()).isEqualTo("name");
        assertThat(v.getRootBeanClass()).isEqualTo(CustomMessageBean.class);
        assertThat(v.getRootBean()).isSameAs(bean);
        assertThat(v.getLeafBean()).isSameAs(bean);
    }

    private Validator validator() {
        return container.getValidator();
    }
}
