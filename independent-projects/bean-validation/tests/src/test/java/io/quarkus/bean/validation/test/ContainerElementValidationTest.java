package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ContainerElementValidationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(ContainerBean.class)
            .build();

    static class ContainerBean {
        List<@NotNull String> names;

        Map<@NotNull String, @Size(min = 1) String> entries;
    }

    @Test
    void validContainerElements() {
        ContainerBean bean = new ContainerBean();
        bean.names = Arrays.asList("Alice", "Bob");
        bean.entries = Map.of("key1", "value1");
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void nullElementInList() {
        ContainerBean bean = new ContainerBean();
        bean.names = Arrays.asList("Alice", null, "Bob");
        bean.entries = Map.of("key1", "value1");
        Set<ConstraintViolation<ContainerBean>> violations = validator().validate(bean);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
    }

    private Validator validator() {
        return container.getValidator();
    }
}
