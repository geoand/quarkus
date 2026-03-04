package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.executable.ExecutableValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests container element constraints on method parameters, return values,
 * and constructor parameters.
 */
class ContainerElementExecutableValidationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(MethodParamBean.class, MethodReturnBean.class,
                    NestedContainerParamBean.class, ConstructorParamBean.class)
            .build();

    // --- Method parameter with container element constraint ---

    static class MethodParamBean {
        public void processNames(List<@NotBlank String> names) {
        }

        public void processMap(Map<@NotNull String, @NotBlank String> entries) {
        }
    }

    @Test
    void methodParamListElementValid() throws Exception {
        MethodParamBean bean = new MethodParamBean();
        Method method = MethodParamBean.class.getMethod("processNames", List.class);
        List<String> validList = List.of("Alice", "Bob");

        Set<ConstraintViolation<MethodParamBean>> violations = execValidator()
                .validateParameters(bean, method, new Object[] { validList });
        assertThat(violations).isEmpty();
    }

    @Test
    void methodParamListElementBlankViolation() throws Exception {
        MethodParamBean bean = new MethodParamBean();
        Method method = MethodParamBean.class.getMethod("processNames", List.class);
        List<String> invalidList = List.of("Alice", "", "Bob");

        Set<ConstraintViolation<MethodParamBean>> violations = execValidator()
                .validateParameters(bean, method, new Object[] { invalidList });
        assertThat(violations).hasSize(1);
    }

    @Test
    void methodParamMapValueBlankViolation() throws Exception {
        MethodParamBean bean = new MethodParamBean();
        Method method = MethodParamBean.class.getMethod("processMap", Map.class);
        Map<String, String> invalidMap = new HashMap<>();
        invalidMap.put("key", "");

        Set<ConstraintViolation<MethodParamBean>> violations = execValidator()
                .validateParameters(bean, method, new Object[] { invalidMap });
        assertThat(violations).hasSize(1);
    }

    // --- Method return value with container element constraint ---

    static class MethodReturnBean {
        public List<@NotBlank String> getNames() {
            return null;
        }

        public Map<String, List<@NotBlank String>> getNestedNames() {
            return null;
        }
    }

    @Test
    void methodReturnListElementValid() throws Exception {
        MethodReturnBean bean = new MethodReturnBean();
        Method method = MethodReturnBean.class.getMethod("getNames");
        List<String> validResult = List.of("Alice", "Bob");

        Set<ConstraintViolation<MethodReturnBean>> violations = execValidator()
                .validateReturnValue(bean, method, validResult);
        assertThat(violations).isEmpty();
    }

    @Test
    void methodReturnListElementBlankViolation() throws Exception {
        MethodReturnBean bean = new MethodReturnBean();
        Method method = MethodReturnBean.class.getMethod("getNames");
        List<String> invalidResult = List.of("Alice", "", "Bob");

        Set<ConstraintViolation<MethodReturnBean>> violations = execValidator()
                .validateReturnValue(bean, method, invalidResult);
        assertThat(violations).hasSize(1);
    }

    @Test
    void methodReturnNestedContainerViolation() throws Exception {
        MethodReturnBean bean = new MethodReturnBean();
        Method method = MethodReturnBean.class.getMethod("getNestedNames");
        Map<String, List<String>> invalidResult = new HashMap<>();
        invalidResult.put("group", Collections.singletonList(""));

        Set<ConstraintViolation<MethodReturnBean>> violations = execValidator()
                .validateReturnValue(bean, method, invalidResult);
        assertThat(violations).hasSize(1);
    }

    // --- Nested container on method parameter ---

    static class NestedContainerParamBean {
        public void process(Map<String, List<@NotBlank String>> data) {
        }
    }

    @Test
    void nestedContainerParamValid() throws Exception {
        NestedContainerParamBean bean = new NestedContainerParamBean();
        Method method = NestedContainerParamBean.class.getMethod("process", Map.class);
        Map<String, List<String>> validData = Map.of("key", List.of("value"));

        Set<ConstraintViolation<NestedContainerParamBean>> violations = execValidator()
                .validateParameters(bean, method, new Object[] { validData });
        assertThat(violations).isEmpty();
    }

    @Test
    void nestedContainerParamBlankViolation() throws Exception {
        NestedContainerParamBean bean = new NestedContainerParamBean();
        Method method = NestedContainerParamBean.class.getMethod("process", Map.class);
        Map<String, List<String>> invalidData = new HashMap<>();
        invalidData.put("key", Collections.singletonList(""));

        Set<ConstraintViolation<NestedContainerParamBean>> violations = execValidator()
                .validateParameters(bean, method, new Object[] { invalidData });
        assertThat(violations).hasSize(1);
    }

    // --- Constructor parameter with container element constraint ---

    static class ConstructorParamBean {
        public ConstructorParamBean(List<@NotBlank String> items) {
        }
    }

    @Test
    void constructorParamValid() throws Exception {
        Constructor<ConstructorParamBean> ctor = ConstructorParamBean.class.getConstructor(List.class);
        List<String> validList = List.of("item1", "item2");

        Set<ConstraintViolation<ConstructorParamBean>> violations = execValidator()
                .validateConstructorParameters(ctor, new Object[] { validList });
        assertThat(violations).isEmpty();
    }

    @Test
    void constructorParamBlankViolation() throws Exception {
        Constructor<ConstructorParamBean> ctor = ConstructorParamBean.class.getConstructor(List.class);
        List<String> invalidList = Collections.singletonList("");

        Set<ConstraintViolation<ConstructorParamBean>> violations = execValidator()
                .validateConstructorParameters(ctor, new Object[] { invalidList });
        assertThat(violations).hasSize(1);
    }

    private Validator validator() {
        return container.getValidator();
    }

    private ExecutableValidator execValidator() {
        return validator().forExecutables();
    }
}
