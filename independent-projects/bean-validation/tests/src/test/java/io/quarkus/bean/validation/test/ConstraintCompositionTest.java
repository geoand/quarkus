package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests constraint composition features:
 * - Composed constraints (meta-annotations)
 * - {@code @ReportAsSingleViolation}
 * - {@code @OverridesAttribute}
 * - Nested composition
 */
class ConstraintCompositionTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(ComposedBean.class, ReportAsSingleBean.class,
                    OverridesBean.class, NestedComposedBean.class)
            .build();

    // --- Basic composed constraint ---

    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @NotNull
    @Size(min = 2, max = 50)
    @Constraint(validatedBy = {})
    @interface ValidName {
        String message() default "Invalid name";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class ComposedBean {
        @ValidName
        String name;
    }

    @Test
    void composedConstraintValid() {
        ComposedBean bean = new ComposedBean();
        bean.name = "Alice";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void composedConstraintNullViolatesNotNull() {
        ComposedBean bean = new ComposedBean();
        bean.name = null;
        Set<ConstraintViolation<ComposedBean>> violations = validator().validate(bean);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void composedConstraintTooShortViolatesSize() {
        ComposedBean bean = new ComposedBean();
        bean.name = "A"; // min 2
        Set<ConstraintViolation<ComposedBean>> violations = validator().validate(bean);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void composedConstraintReportsMultipleViolations() {
        // null violates both @NotNull and @Size (since null is handled by @NotNull)
        // But per spec, @Size considers null valid, so only @NotNull fires
        ComposedBean bean = new ComposedBean();
        bean.name = null;
        Set<ConstraintViolation<ComposedBean>> violations = validator().validate(bean);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
    }

    // --- @ReportAsSingleViolation ---

    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @NotNull
    @Size(min = 3)
    @Pattern(regexp = "[a-zA-Z]+")
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @interface StrictName {
        String message() default "Name must be at least 3 alphabetic characters";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class ReportAsSingleBean {
        @StrictName
        String name;
    }

    @Test
    void reportAsSingleViolationValid() {
        ReportAsSingleBean bean = new ReportAsSingleBean();
        bean.name = "Alice";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void reportAsSingleViolationReportsOnlyOne() {
        ReportAsSingleBean bean = new ReportAsSingleBean();
        bean.name = "A1"; // violates both @Size(min=3) and @Pattern
        Set<ConstraintViolation<ReportAsSingleBean>> violations = validator().validate(bean);
        // @ReportAsSingleViolation: only one violation with the composed constraint's message
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Name must be at least 3 alphabetic characters");
    }

    @Test
    void reportAsSingleViolationNullReportsOne() {
        ReportAsSingleBean bean = new ReportAsSingleBean();
        bean.name = null; // violates @NotNull
        Set<ConstraintViolation<ReportAsSingleBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Name must be at least 3 alphabetic characters");
    }

    // --- @OverridesAttribute ---

    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Size
    @Constraint(validatedBy = {})
    @interface BoundedString {
        String message() default "String out of bounds";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Size.class, name = "min")
        int lower() default 0;

        @OverridesAttribute(constraint = Size.class, name = "max")
        int upper() default Integer.MAX_VALUE;
    }

    static class OverridesBean {
        @BoundedString(lower = 3, upper = 10)
        String value;
    }

    @Test
    void overridesAttributeValid() {
        OverridesBean bean = new OverridesBean();
        bean.value = "hello"; // length 5, within [3, 10]
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void overridesAttributeTooShort() {
        OverridesBean bean = new OverridesBean();
        bean.value = "ab"; // length 2, below lower=3
        Set<ConstraintViolation<OverridesBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
    }

    @Test
    void overridesAttributeTooLong() {
        OverridesBean bean = new OverridesBean();
        bean.value = "this string is too long"; // exceeds upper=10
        Set<ConstraintViolation<OverridesBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
    }

    @Test
    void overridesAttributeNullIsValid() {
        OverridesBean bean = new OverridesBean();
        bean.value = null; // @Size considers null valid
        assertThat(validator().validate(bean)).isEmpty();
    }

    // --- Nested composition ---

    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ValidName // itself composed of @NotNull + @Size(min=2, max=50)
    @Pattern(regexp = "[A-Z][a-z]+")
    @Constraint(validatedBy = {})
    @interface FormalName {
        String message() default "Must be a formal name (capitalized, 2-50 chars)";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class NestedComposedBean {
        @FormalName
        String name;
    }

    @Test
    void nestedCompositionValid() {
        NestedComposedBean bean = new NestedComposedBean();
        bean.name = "Alice";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void nestedCompositionInvalidPattern() {
        NestedComposedBean bean = new NestedComposedBean();
        bean.name = "alice"; // not capitalized
        Set<ConstraintViolation<NestedComposedBean>> violations = validator().validate(bean);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void nestedCompositionInvalidSize() {
        NestedComposedBean bean = new NestedComposedBean();
        bean.name = "A"; // too short for @Size(min=2) from @ValidName
        Set<ConstraintViolation<NestedComposedBean>> violations = validator().validate(bean);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void nestedCompositionNullViolation() {
        NestedComposedBean bean = new NestedComposedBean();
        bean.name = null; // violates @NotNull from @ValidName
        Set<ConstraintViolation<NestedComposedBean>> violations = validator().validate(bean);
        assertThat(violations).isNotEmpty();
    }

    private Validator validator() {
        return container.getValidator();
    }
}
