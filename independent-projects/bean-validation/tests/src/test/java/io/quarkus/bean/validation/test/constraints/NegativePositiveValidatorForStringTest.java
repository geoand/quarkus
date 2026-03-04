/*
 * Adapted from Hibernate Validator's NegativePositiveValidatorForStringTest.
 * Original authors: Guillaume Smet
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.NegativeOrZeroValidator;
import io.quarkus.bean.validation.impl.constraints.NegativeValidator;
import io.quarkus.bean.validation.impl.constraints.PositiveOrZeroValidator;
import io.quarkus.bean.validation.impl.constraints.PositiveValidator;

class NegativePositiveValidatorForStringTest {

    // Note: The bean-validation project does not have CharSequence sign validators.
    // These tests validate the Number-based validators with parsed string values
    // through the ForNumber validator, which accepts Number types.
    // If CharSequence sign validators are added later, these tests can be updated.

    // For now, we test the sign validators with values that would come from string parsing.

    @Test
    void testIsValidPositiveValidator() {
        Positive m = annotation(Positive.class);

        PositiveValidator.ForNumber constraint = new PositiveValidator.ForNumber();
        constraint.initialize(m);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(15, null)).isTrue();
        assertThat(constraint.isValid(0, null)).isFalse();
        assertThat(constraint.isValid(-10, null)).isFalse();
    }

    @Test
    void testIsValidPositiveOrZeroValidator() {
        PositiveOrZero m = annotation(PositiveOrZero.class);

        PositiveOrZeroValidator.ForNumber constraint = new PositiveOrZeroValidator.ForNumber();
        constraint.initialize(m);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(15, null)).isTrue();
        assertThat(constraint.isValid(0, null)).isTrue();
        assertThat(constraint.isValid(-10, null)).isFalse();
    }

    @Test
    void testIsValidNegativeValidator() {
        Negative m = annotation(Negative.class);

        NegativeValidator.ForNumber constraint = new NegativeValidator.ForNumber();
        constraint.initialize(m);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(15, null)).isFalse();
        assertThat(constraint.isValid(0, null)).isFalse();
        assertThat(constraint.isValid(-10, null)).isTrue();
    }

    @Test
    void testIsValidNegativeOrZeroValidator() {
        NegativeOrZero m = annotation(NegativeOrZero.class);

        NegativeOrZeroValidator.ForNumber constraint = new NegativeOrZeroValidator.ForNumber();
        constraint.initialize(m);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(15, null)).isFalse();
        assertThat(constraint.isValid(0, null)).isTrue();
        assertThat(constraint.isValid(-10, null)).isTrue();
    }
}
