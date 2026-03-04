/*
 * Adapted from Hibernate Validator's EmailValidatorTest.
 * Original authors: Hardy Ferentschik, Guillaume Smet
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.EmailValidator;

class EmailValidatorTest {

    private static EmailValidator validator;

    @BeforeAll
    static void init() {
        validator = new EmailValidator();
    }

    @Test
    void testNullAndEmptyString() {
        // null is valid per spec
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void testValidEmail() {
        isValidEmail("emmanuel@hibernate.org");
        isValidEmail("emma-n_uel@hibernate");
        isValidEmail("emma+nuel@hibernate.org");
        isValidEmail("emma=nuel@hibernate.org");
        isValidEmail("*@example.net");
        isValidEmail("fred&barny@example.com");
        isValidEmail("---@example.com");
        isValidEmail("foo-bar@example.net");
        isValidEmail("mailbox.sub1.sub2@this-domain");
        isValidEmail("prettyandsimple@example.com");
        isValidEmail("very.common@example.com");
        isValidEmail("disposable.style.email.with+symbol@example.com");
        isValidEmail("other.email-with-dash@example.com");
        isValidEmail("x@example.com");
        isValidEmail("example-indeed@strange-example.com");
        isValidEmail("admin@mailserver1");
        isValidEmail("#!$%&'*+-/=?^_`{}|~@example.org");
        isValidEmail("example@localhost");
        isValidEmail("example@s.solutions");
        isValidEmail("user@localserver");
        isValidEmail("user@tt");
    }

    @Test
    void testInvalidEmail() {
        isInvalidEmail("emmanuel.hibernate.org");
        isInvalidEmail("emma nuel@hibernate.org");
        isInvalidEmail("emma(nuel@hibernate.org");
        isInvalidEmail("emmanuel@");
        isInvalidEmail("emma\nnuel@hibernate.org");
        isInvalidEmail("emma@nuel@hibernate.org");
        isInvalidEmail("Just a string");
        isInvalidEmail("string");
        isInvalidEmail("me@");
        isInvalidEmail("@example.com");
        isInvalidEmail("me@example..com");
        isInvalidEmail("Abc.example.com");
        isInvalidEmail("A@b@c@example.com");
        isInvalidEmail("john.doe@example..com");
    }

    @Test
    void testAccent() {
        isValidEmail("Test^Email@example.com");
    }

    @Test
    void testValidEmailCharSequence() {
        isValidEmail(new StringBuilder("emmanuel@hibernate.org"));
        isInvalidEmail(new StringBuilder("@example.com"));
    }

    @Test
    void testEmailWithTrailingAt() {
        isInvalidEmail("validation@hibernate.com@");
        isInvalidEmail("validation@hibernate.com@@");
        isInvalidEmail("validation@hibernate.com@@@");
    }

    @Test
    void testEmailAddressLength() {
        isValidEmail("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@hibernate.org");
    }

    private void isValidEmail(CharSequence email) {
        assertThat(validator.isValid(email, null))
                .as("Expected '%s' to be a valid email", email)
                .isTrue();
    }

    private void isInvalidEmail(CharSequence email) {
        assertThat(validator.isValid(email, null))
                .as("Expected '%s' to be an invalid email", email)
                .isFalse();
    }
}
