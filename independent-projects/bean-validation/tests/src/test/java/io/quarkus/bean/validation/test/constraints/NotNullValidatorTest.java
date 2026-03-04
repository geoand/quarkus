/*
 * Adapted from Hibernate Validator's NotNullValidatorTest.
 * Original authors: Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.NotNullValidator;

class NotNullValidatorTest {

    @Test
    void testIsValid() {
        NotNullValidator constraint = new NotNullValidator();

        assertThat(constraint.isValid(null, null)).isFalse();
        assertThat(constraint.isValid(new Object(), null)).isTrue();
    }
}
