/*
 * Adapted from Hibernate Validator's NullValidatorTest.
 * Original authors: Alaa Nassef
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.NullValidator;

class NullValidatorTest {

    private static NullValidator constraint;

    @BeforeAll
    static void init() {
        constraint = new NullValidator();
    }

    @Test
    void testIsValid() {
        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(new Object(), null)).isFalse();
    }
}
