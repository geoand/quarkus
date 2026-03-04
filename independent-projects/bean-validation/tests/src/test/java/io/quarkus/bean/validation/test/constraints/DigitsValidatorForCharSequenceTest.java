/*
 * Adapted from Hibernate Validator's DigitsValidatorForCharSequenceTest.
 * Original authors: Alaa Nassef
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.attrs;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Digits;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.DigitsValidator;

class DigitsValidatorForCharSequenceTest {

    private static DigitsValidator.ForCharSequence constraint;

    @BeforeAll
    static void init() {
        Digits p = annotation(Digits.class, attrs("integer", 5, "fraction", 2));

        constraint = new DigitsValidator.ForCharSequence();
        constraint.initialize(p);
    }

    @Test
    void testIsValid() {
        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid("0", null)).isTrue();
        assertThat(constraint.isValid("500.2", null)).isTrue();
        assertThat(constraint.isValid("-12456.22", null)).isTrue();
        assertThat(constraint.isValid("-000000000.22", null)).isTrue();
        // should throw number format exception
        assertThat(constraint.isValid("", null)).isFalse();
        assertThat(constraint.isValid("256874.0", null)).isFalse();
        assertThat(constraint.isValid("12.0001", null)).isFalse();
    }

    @Test
    void testIsValidCharSequence() {
        assertThat(constraint.isValid(new StringBuilder("500.2"), null)).isTrue();
    }
}
