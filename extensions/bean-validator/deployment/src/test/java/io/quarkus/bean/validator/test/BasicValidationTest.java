package io.quarkus.bean.validator.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class BasicValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap
                    .create(JavaArchive.class)
                    .addClasses(TestBean.class));

    @Inject
    Validator validator;

    @Inject
    ValidatorFactory validatorFactory;

    @Test
    void validatorInjected() {
        assertThat(validator).isNotNull();
        assertThat(validatorFactory).isNotNull();
    }

    @Test
    void validBeanHasNoViolations() {
        TestBean bean = new TestBean();
        bean.name = "Alice";
        bean.email = "alice@example.com";

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        assertThat(violations).isEmpty();
    }

    @Test
    void nullNameViolation() {
        TestBean bean = new TestBean();
        bean.email = "alice@example.com";

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
    }

    @Test
    void blankEmailViolation() {
        TestBean bean = new TestBean();
        bean.name = "Alice";
        bean.email = "";

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
    }

    @Test
    void sizeTooLong() {
        TestBean bean = new TestBean();
        bean.name = "A".repeat(101);
        bean.email = "test@test.com";

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        assertThat(violations).hasSize(1);
    }

    @Test
    void multipleViolations() {
        TestBean bean = new TestBean();

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        assertThat(violations).hasSize(2);
    }

    public static class TestBean {
        @NotNull
        @Size(max = 100)
        String name;

        @NotBlank
        String email;
    }
}
