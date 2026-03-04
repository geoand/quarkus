/*
 * Adapted from Hibernate Validator's MinValidatorForStringTest and MaxValidatorForStringTest.
 * Original authors: Alaa Nassef, Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.attrs;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.DecimalMaxValidator;
import io.quarkus.bean.validation.impl.constraints.DecimalMinValidator;
import io.quarkus.bean.validation.impl.constraints.MaxValidator;
import io.quarkus.bean.validation.impl.constraints.MinValidator;

class MinMaxValidatorForStringTest {

    // --- Min for String ---

    @Test
    void testIsValidMinValidator() {
        Min m = annotation(Min.class, "value", 15L);

        MinValidator.ForCharSequence constraint = new MinValidator.ForCharSequence();
        constraint.initialize(m);
        testMinValidator(constraint, true);
    }

    @Test
    void testIsValidDecimalMinValidator() {
        DecimalMin m = annotation(DecimalMin.class, "value", "1500E-2");

        DecimalMinValidator.ForCharSequence constraint = new DecimalMinValidator.ForCharSequence();
        constraint.initialize(m);
        testMinValidator(constraint, true);
    }

    @Test
    void testIsValidDecimalMinExclusive() {
        DecimalMin m = annotation(DecimalMin.class, attrs("value", "1500E-2", "inclusive", false));

        DecimalMinValidator.ForCharSequence constraint = new DecimalMinValidator.ForCharSequence();
        constraint.initialize(m);
        testMinValidator(constraint, false);
    }

    private void testMinValidator(ConstraintValidator<?, CharSequence> constraint, boolean inclusive) {
        if (inclusive) {
            assertThat(constraint.isValid("15", null)).isTrue();
            assertThat(constraint.isValid("15.0", null)).isTrue();
        } else {
            assertThat(constraint.isValid("15", null)).isFalse();
            assertThat(constraint.isValid("15.0", null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid("20", null)).isTrue();
        assertThat(constraint.isValid("10", null)).isFalse();
        assertThat(constraint.isValid("14.99", null)).isFalse();
        assertThat(constraint.isValid("-14.99", null)).isFalse();

        // CharSequence support
        assertThat(constraint.isValid(new StringBuilder("20"), null)).isTrue();

        // number format exception
        assertThat(constraint.isValid("15l", null)).isFalse();
    }

    // --- Max for String ---

    @Test
    void testIsValidMaxValidator() {
        Max m = annotation(Max.class, "value", 15L);

        MaxValidator.ForCharSequence constraint = new MaxValidator.ForCharSequence();
        constraint.initialize(m);
        testMaxValidator(constraint, true);
    }

    @Test
    void testIsValidDecimalMaxValidator() {
        DecimalMax m = annotation(DecimalMax.class, "value", "15.0E0");

        DecimalMaxValidator.ForCharSequence constraint = new DecimalMaxValidator.ForCharSequence();
        constraint.initialize(m);
        testMaxValidator(constraint, true);
    }

    @Test
    void testIsValidDecimalMaxExclusive() {
        DecimalMax m = annotation(DecimalMax.class, attrs("value", "15.0E0", "inclusive", false));

        DecimalMaxValidator.ForCharSequence constraint = new DecimalMaxValidator.ForCharSequence();
        constraint.initialize(m);
        testMaxValidator(constraint, false);
    }

    private void testMaxValidator(ConstraintValidator<?, CharSequence> constraint, boolean inclusive) {
        if (inclusive) {
            assertThat(constraint.isValid("15", null)).isTrue();
            assertThat(constraint.isValid("15.0", null)).isTrue();
        } else {
            assertThat(constraint.isValid("15", null)).isFalse();
            assertThat(constraint.isValid("15.0", null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid("10", null)).isTrue();
        assertThat(constraint.isValid("14.99", null)).isTrue();
        assertThat(constraint.isValid("-14.99", null)).isTrue();
        assertThat(constraint.isValid("20", null)).isFalse();

        // CharSequence support
        assertThat(constraint.isValid(new StringBuilder("10"), null)).isTrue();

        // number format exception
        assertThat(constraint.isValid("15l", null)).isFalse();
    }
}
