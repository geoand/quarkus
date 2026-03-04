/*
 * Adapted from Hibernate Validator's AssertTrueValidatorTest.
 * Original authors: Alaa Nassef
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.AssertTrueValidator;

class AssertTrueValidatorTest {

    private static AssertTrueValidator constraint;

    @BeforeAll
    static void init() {
        constraint = new AssertTrueValidator();
    }

    @Test
    void testIsValid() {
        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(true, null)).isTrue();
        assertThat(constraint.isValid(Boolean.TRUE, null)).isTrue();
        assertThat(constraint.isValid(false, null)).isFalse();
        assertThat(constraint.isValid(Boolean.FALSE, null)).isFalse();
    }
}
