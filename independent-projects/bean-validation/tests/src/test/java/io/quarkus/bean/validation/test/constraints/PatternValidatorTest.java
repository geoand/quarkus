/*
 * Adapted from Hibernate Validator's PatternValidatorTest.
 * Original authors: Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.constraints.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.PatternValidator;

class PatternValidatorTest {

    @Test
    void testIsValid() {
        Pattern p = annotation(Pattern.class, "regexp", "foobar");

        PatternValidator constraint = new PatternValidator();
        constraint.initialize(p);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid("", null)).isFalse();
        assertThat(constraint.isValid("bla bla", null)).isFalse();
        assertThat(constraint.isValid("This test is not foobar", null)).isFalse();
    }

    @Test
    void testIsValidForCharSequence() {
        Pattern p = annotation(Pattern.class, "regexp", "char sequence");

        PatternValidator constraint = new PatternValidator();
        constraint.initialize(p);

        assertThat(constraint.isValid(new StringBuilder("char sequence"), null)).isTrue();
    }

    @Test
    void testIsValidForEmptyStringRegexp() {
        Pattern p = annotation(Pattern.class, "regexp", "|^.*foo$");

        PatternValidator constraint = new PatternValidator();
        constraint.initialize(p);

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid("", null)).isTrue();
        assertThat(constraint.isValid("bla bla", null)).isFalse();
        assertThat(constraint.isValid("foo", null)).isTrue();
        assertThat(constraint.isValid("a b c foo", null)).isTrue();
    }

    @Test
    void testInvalidRegularExpression() {
        Pattern p = annotation(Pattern.class, "regexp", "(unbalanced parentheses");

        PatternValidator constraint = new PatternValidator();
        assertThatThrownBy(() -> constraint.initialize(p))
                .isInstanceOf(java.util.regex.PatternSyntaxException.class);
    }
}
