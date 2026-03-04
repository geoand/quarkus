package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MessageInterpolationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(MessageBean.class)
            .build();

    static class MessageBean {
        @NotNull
        String required;

        @Size(min = 2, max = 10)
        String sized;

        @Min(5)
        int minimum;
    }

    @Test
    void notNullMessage() {
        MessageBean bean = new MessageBean();
        bean.required = null;
        bean.sized = "hello";
        bean.minimum = 10;

        Set<ConstraintViolation<MessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        ConstraintViolation<MessageBean> violation = violations.iterator().next();
        assertThat(violation.getMessageTemplate()).isEqualTo("{jakarta.validation.constraints.NotNull.message}");
        assertThat(violation.getMessage()).isNotEmpty();
    }

    @Test
    void sizeMessage() {
        MessageBean bean = new MessageBean();
        bean.required = "x";
        bean.sized = "x"; // too short
        bean.minimum = 10;

        Set<ConstraintViolation<MessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        ConstraintViolation<MessageBean> violation = violations.iterator().next();
        assertThat(violation.getMessageTemplate()).isEqualTo("{jakarta.validation.constraints.Size.message}");
    }

    @Test
    void violationContainsInvalidValue() {
        MessageBean bean = new MessageBean();
        bean.required = "ok";
        bean.sized = "hello";
        bean.minimum = 1; // below minimum of 5

        Set<ConstraintViolation<MessageBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        ConstraintViolation<MessageBean> violation = violations.iterator().next();
        assertThat(violation.getInvalidValue()).isEqualTo(1);
        assertThat(violation.getRootBean()).isSameAs(bean);
        assertThat(violation.getRootBeanClass()).isEqualTo(MessageBean.class);
    }

    private Validator validator() {
        return container.getValidator();
    }
}
