/*
 * Adapted from Hibernate Validator's AssertFalseValidatorTest.
 * Original authors: Alaa Nassef
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.AssertFalseValidator;

class AssertFalseValidatorTest {

    private static AssertFalseValidator constraint;

    @BeforeAll
    static void init() {
        constraint = new AssertFalseValidator();
    }

    @Test
    void testIsValid() {
        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(false, null)).isTrue();
        assertThat(constraint.isValid(Boolean.FALSE, null)).isTrue();
        assertThat(constraint.isValid(true, null)).isFalse();
        assertThat(constraint.isValid(Boolean.TRUE, null)).isFalse();
    }
}
