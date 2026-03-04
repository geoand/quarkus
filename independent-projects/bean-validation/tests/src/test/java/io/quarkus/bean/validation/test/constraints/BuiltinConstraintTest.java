package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.bean.validation.test.BeanValidationTestContainer;

class BuiltinConstraintTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(BooleanBean.class, NullBean.class, StringBean.class,
                    NumericBean.class, TemporalBean.class, CollectionBean.class)
            .build();

    // --- Boolean constraints ---

    static class BooleanBean {
        @AssertTrue
        Boolean assertTrue;
        @AssertFalse
        Boolean assertFalse;
    }

    @Test
    void assertTrueValid() {
        BooleanBean bean = new BooleanBean();
        bean.assertTrue = true;
        bean.assertFalse = false;
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void assertTrueInvalid() {
        BooleanBean bean = new BooleanBean();
        bean.assertTrue = false;
        bean.assertFalse = false;
        Set<ConstraintViolation<BooleanBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("assertTrue");
    }

    @Test
    void assertFalseInvalid() {
        BooleanBean bean = new BooleanBean();
        bean.assertTrue = true;
        bean.assertFalse = true;
        Set<ConstraintViolation<BooleanBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("assertFalse");
    }

    @Test
    void assertTrueNullIsValid() {
        BooleanBean bean = new BooleanBean();
        // Both null - should be valid (null handling per spec)
        assertThat(validator().validate(bean)).isEmpty();
    }

    // --- Null constraints ---

    static class NullBean {
        @Null
        Object mustBeNull;
        @NotNull
        Object mustNotBeNull;
    }

    @Test
    void nullConstraints() {
        NullBean bean = new NullBean();
        bean.mustBeNull = null;
        bean.mustNotBeNull = "hello";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void nullConstraintViolations() {
        NullBean bean = new NullBean();
        bean.mustBeNull = "not null";
        bean.mustNotBeNull = null;
        Set<ConstraintViolation<NullBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(2);
    }

    // --- String constraints ---

    static class StringBean {
        @NotBlank
        String notBlank;
        @NotEmpty
        String notEmpty;
        @Size(min = 2, max = 10)
        String sized;
        @Pattern(regexp = "[a-z]+")
        String pattern;
        @Email
        String email;
    }

    @Test
    void stringConstraintsValid() {
        StringBean bean = new StringBean();
        bean.notBlank = "hello";
        bean.notEmpty = "world";
        bean.sized = "hello";
        bean.pattern = "abc";
        bean.email = "test@example.com";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void notBlankViolation() {
        StringBean bean = new StringBean();
        bean.notBlank = "   ";
        bean.notEmpty = "x";
        bean.sized = "hello";
        bean.pattern = "abc";
        bean.email = "test@example.com";
        Set<ConstraintViolation<StringBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("notBlank");
    }

    @Test
    void sizeViolation() {
        StringBean bean = new StringBean();
        bean.notBlank = "x";
        bean.notEmpty = "x";
        bean.sized = "x"; // too short
        bean.pattern = "a";
        bean.email = "a@b.com";
        Set<ConstraintViolation<StringBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("sized");
    }

    @Test
    void patternViolation() {
        StringBean bean = new StringBean();
        bean.notBlank = "x";
        bean.notEmpty = "x";
        bean.sized = "hello";
        bean.pattern = "ABC123"; // uppercase not allowed
        bean.email = "a@b.com";
        Set<ConstraintViolation<StringBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("pattern");
    }

    @Test
    void emailViolation() {
        StringBean bean = new StringBean();
        bean.notBlank = "x";
        bean.notEmpty = "x";
        bean.sized = "hello";
        bean.pattern = "abc";
        bean.email = "not-an-email";
        Set<ConstraintViolation<StringBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
    }

    // --- Numeric constraints ---

    static class NumericBean {
        @Min(5)
        int min;
        @Max(100)
        int max;
        @DecimalMin("10.5")
        BigDecimal decimalMin;
        @DecimalMax("99.9")
        BigDecimal decimalMax;
        @Digits(integer = 3, fraction = 2)
        BigDecimal digits;
        @Positive
        int positive;
        @PositiveOrZero
        int positiveOrZero;
        @Negative
        int negative;
        @NegativeOrZero
        int negativeOrZero;
    }

    @Test
    void numericConstraintsValid() {
        NumericBean bean = new NumericBean();
        bean.min = 10;
        bean.max = 50;
        bean.decimalMin = new BigDecimal("15.0");
        bean.decimalMax = new BigDecimal("50.0");
        bean.digits = new BigDecimal("123.45");
        bean.positive = 1;
        bean.positiveOrZero = 0;
        bean.negative = -1;
        bean.negativeOrZero = 0;
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void minViolation() {
        NumericBean bean = new NumericBean();
        bean.min = 1; // less than 5
        bean.max = 50;
        bean.decimalMin = new BigDecimal("15.0");
        bean.decimalMax = new BigDecimal("50.0");
        bean.digits = new BigDecimal("123.45");
        bean.positive = 1;
        bean.positiveOrZero = 0;
        bean.negative = -1;
        bean.negativeOrZero = 0;
        Set<ConstraintViolation<NumericBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("min");
    }

    @Test
    void positiveViolation() {
        NumericBean bean = new NumericBean();
        bean.min = 10;
        bean.max = 50;
        bean.decimalMin = new BigDecimal("15.0");
        bean.decimalMax = new BigDecimal("50.0");
        bean.digits = new BigDecimal("123.45");
        bean.positive = 0; // must be > 0
        bean.positiveOrZero = 0;
        bean.negative = -1;
        bean.negativeOrZero = 0;
        Set<ConstraintViolation<NumericBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("positive");
    }

    // --- Temporal constraints ---

    static class TemporalBean {
        @Past
        LocalDate past;
        @Future
        LocalDate future;
    }

    @Test
    void temporalConstraintsValid() {
        TemporalBean bean = new TemporalBean();
        bean.past = LocalDate.now().minusDays(1);
        bean.future = LocalDate.now().plusDays(1);
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void pastViolation() {
        TemporalBean bean = new TemporalBean();
        bean.past = LocalDate.now().plusDays(1); // future, not past
        bean.future = LocalDate.now().plusDays(1);
        Set<ConstraintViolation<TemporalBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("past");
    }

    @Test
    void temporalNullIsValid() {
        TemporalBean bean = new TemporalBean();
        // Both null - should be valid
        assertThat(validator().validate(bean)).isEmpty();
    }

    // --- Collection constraints ---

    static class CollectionBean {
        @NotEmpty
        List<String> notEmptyList;
        @Size(min = 1, max = 3)
        List<String> sizedList;
        @NotEmpty
        Map<String, String> notEmptyMap;
    }

    @Test
    void collectionConstraintsValid() {
        CollectionBean bean = new CollectionBean();
        bean.notEmptyList = Arrays.asList("a", "b");
        bean.sizedList = Arrays.asList("x");
        bean.notEmptyMap = Collections.singletonMap("k", "v");
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void notEmptyListViolation() {
        CollectionBean bean = new CollectionBean();
        bean.notEmptyList = Collections.emptyList();
        bean.sizedList = Arrays.asList("x");
        bean.notEmptyMap = Collections.singletonMap("k", "v");
        Set<ConstraintViolation<CollectionBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("notEmptyList");
    }

    private Validator validator() {
        return container.getValidator();
    }
}
