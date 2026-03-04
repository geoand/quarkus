/*
 * Adapted from Hibernate Validator's BlankValidatorTest.
 * Original authors: Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.bean.validation.impl.constraints.NotBlankValidator;
import io.quarkus.bean.validation.test.BeanValidationTestContainer;

class NotBlankValidatorTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(Foo.class)
            .build();

    @Test
    void testConstraintValidator() {
        NotBlankValidator constraintValidator = new NotBlankValidator();

        assertThat(constraintValidator.isValid("a", null)).isTrue();
        assertThat(constraintValidator.isValid(null, null)).isFalse();
        assertThat(constraintValidator.isValid("", null)).isFalse();
        assertThat(constraintValidator.isValid(" ", null)).isFalse();
        assertThat(constraintValidator.isValid("\t", null)).isFalse();
        assertThat(constraintValidator.isValid("\n", null)).isFalse();
    }

    @Test
    void testNotBlank() {
        Validator validator = container.getValidator();
        Foo foo = new Foo();

        Set<ConstraintViolation<Foo>> violations = validator.validate(foo);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getConstraintDescriptor().getAnnotation()).isInstanceOf(NotBlank.class);

        foo.name = "";
        violations = validator.validate(foo);
        assertThat(violations).hasSize(1);

        foo.name = " ";
        violations = validator.validate(foo);
        assertThat(violations).hasSize(1);

        foo.name = "\t";
        violations = validator.validate(foo);
        assertThat(violations).hasSize(1);

        foo.name = "\n";
        violations = validator.validate(foo);
        assertThat(violations).hasSize(1);

        foo.name = "john doe";
        violations = validator.validate(foo);
        assertThat(violations).isEmpty();
    }

    static class Foo {
        @NotBlank
        String name;
    }
}
