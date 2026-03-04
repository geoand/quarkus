package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.executable.ExecutableValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MethodValidationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(ServiceBean.class)
            .build();

    static class ServiceBean {
        public String greet(@NotNull @Size(min = 1) String name) {
            return "Hello, " + name;
        }

        @NotNull
        public String process(@NotNull String input) {
            return input != null ? input.toUpperCase() : null;
        }
    }

    @Test
    void validParameters() throws Exception {
        ServiceBean bean = new ServiceBean();
        Method method = ServiceBean.class.getMethod("greet", String.class);
        ExecutableValidator execValidator = validator().forExecutables();

        Set<ConstraintViolation<ServiceBean>> violations = execValidator.validateParameters(
                bean, method, new Object[] { "World" });
        assertThat(violations).isEmpty();
    }

    @Test
    void nullParameterViolation() throws Exception {
        ServiceBean bean = new ServiceBean();
        Method method = ServiceBean.class.getMethod("greet", String.class);
        ExecutableValidator execValidator = validator().forExecutables();

        Set<ConstraintViolation<ServiceBean>> violations = execValidator.validateParameters(
                bean, method, new Object[] { null });
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void validReturnValue() throws Exception {
        ServiceBean bean = new ServiceBean();
        Method method = ServiceBean.class.getMethod("process", String.class);
        ExecutableValidator execValidator = validator().forExecutables();

        Set<ConstraintViolation<ServiceBean>> violations = execValidator.validateReturnValue(
                bean, method, "RESULT");
        assertThat(violations).isEmpty();
    }

    @Test
    void nullReturnValueViolation() throws Exception {
        ServiceBean bean = new ServiceBean();
        Method method = ServiceBean.class.getMethod("process", String.class);
        ExecutableValidator execValidator = validator().forExecutables();

        Set<ConstraintViolation<ServiceBean>> violations = execValidator.validateReturnValue(
                bean, method, null);
        assertThat(violations).hasSize(1);
    }

    private Validator validator() {
        return container.getValidator();
    }
}
