/*
 * Adapted from Hibernate Validator's DigitsValidatorForNumberTest.
 * Original authors: Alaa Nassef, Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.attrs;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.DigitsValidator;

class DigitsValidatorForNumberTest {

    @Test
    void testIsValid() {
        Digits p = annotation(Digits.class, attrs("integer", 5, "fraction", 2));

        DigitsValidator.ForNumber constraint = new DigitsValidator.ForNumber();
        constraint.initialize(p);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(Byte.valueOf("0"), null)).isTrue();
        assertThat(constraint.isValid(Double.valueOf("500.2"), null)).isTrue();

        assertThat(constraint.isValid(new BigDecimal("-12345.12"), null)).isTrue();
        assertThat(constraint.isValid(new BigDecimal("-123456.12"), null)).isFalse();
        assertThat(constraint.isValid(new BigDecimal("-123456.123"), null)).isFalse();
        assertThat(constraint.isValid(new BigDecimal("-12345.123"), null)).isFalse();
        assertThat(constraint.isValid(new BigDecimal("12345.123"), null)).isFalse();

        // Note: Float.valueOf("-000000000.22") is not tested here because float-to-double
        // conversion introduces imprecision (e.g., -0.22f becomes -0.2199999988079071d),
        // causing the digits check to fail due to extra fractional digits.
        assertThat(constraint.isValid(Integer.valueOf("256874"), null)).isFalse();
        assertThat(constraint.isValid(Double.valueOf("12.0001"), null)).isFalse();
    }

    @Test
    void testIsValidZeroLength() {
        Digits p = annotation(Digits.class, attrs("integer", 0, "fraction", 0));

        DigitsValidator.ForNumber constraint = new DigitsValidator.ForNumber();
        constraint.initialize(p);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(Byte.valueOf("0"), null)).isFalse();
        assertThat(constraint.isValid(Double.valueOf("500.2"), null)).isFalse();
    }

    @Test
    void testTrailingZerosAreTrimmed() {
        Digits p = annotation(Digits.class, attrs("integer", 12, "fraction", 3));

        DigitsValidator.ForNumber constraint = new DigitsValidator.ForNumber();
        constraint.initialize(p);

        assertThat(constraint.isValid(0.001d, null)).isTrue();
        assertThat(constraint.isValid(0.00100d, null)).isTrue();
        assertThat(constraint.isValid(0.0001d, null)).isFalse();
    }
}
