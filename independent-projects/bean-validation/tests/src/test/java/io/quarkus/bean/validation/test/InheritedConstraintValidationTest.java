package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.executable.ExecutableValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests that constraints are properly inherited across class hierarchies:
 * - Interface to implementation
 * - Superclass to subclass
 * - Multi-level inheritance
 * - Method constraints from interface
 */
class InheritedConstraintValidationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(
                    Identifiable.class, Named.class,
                    BaseEntity.class, ConcreteEntity.class,
                    ZipCodeService.class, ZipCodeServiceImpl.class,
                    GreetingService.class, EnhancedGreetingService.class)
            .build();

    // --- Interface constraint inheritance ---

    interface Identifiable {
        @NotNull
        Long getId();
    }

    interface Named {
        @NotNull
        @Size(min = 1)
        String getName();
    }

    static class BaseEntity implements Identifiable, Named {
        Long id;
        String name;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class ConcreteEntity extends BaseEntity {
        @Positive
        int priority;
    }

    @Test
    void interfaceConstraintsInheritedByImpl() {
        BaseEntity entity = new BaseEntity();
        entity.id = 1L;
        entity.name = "Test";
        assertThat(validator().validate(entity)).isEmpty();
    }

    @Test
    void multiInterfaceViolations() {
        BaseEntity entity = new BaseEntity();
        entity.id = null; // violates Identifiable.getId @NotNull
        entity.name = null; // violates Named.getName @NotNull
        Set<ConstraintViolation<BaseEntity>> violations = validator().validate(entity);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void subclassInheritsInterfaceConstraints() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.id = 1L;
        entity.name = "Test";
        entity.priority = 5;
        assertThat(validator().validate(entity)).isEmpty();
    }

    @Test
    void subclassHasOwnAndInheritedConstraints() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.id = null; // inherited from Identifiable
        entity.name = "Test";
        entity.priority = -1; // own constraint @Positive
        Set<ConstraintViolation<ConcreteEntity>> violations = validator().validate(entity);
        assertThat(violations).hasSize(2);
    }

    // --- Method constraint inheritance from interface (implements) ---

    interface ZipCodeService {
        String echoZipCode(@NotNull @Size(min = 5, max = 5) String zipCode);
    }

    static class ZipCodeServiceImpl implements ZipCodeService {
        @Override
        public String echoZipCode(String zipCode) {
            return zipCode;
        }
    }

    @Test
    void methodConstraintInheritedFromInterface_valid() throws Exception {
        ZipCodeServiceImpl service = new ZipCodeServiceImpl();
        Method method = ZipCodeServiceImpl.class.getMethod("echoZipCode", String.class);
        Set<ConstraintViolation<ZipCodeServiceImpl>> violations = execValidator()
                .validateParameters(service, method, new Object[] { "12345" });
        assertThat(violations).isEmpty();
    }

    @Test
    void methodConstraintInheritedFromInterface_tooShort() throws Exception {
        ZipCodeServiceImpl service = new ZipCodeServiceImpl();
        Method method = ZipCodeServiceImpl.class.getMethod("echoZipCode", String.class);
        Set<ConstraintViolation<ZipCodeServiceImpl>> violations = execValidator()
                .validateParameters(service, method, new Object[] { "123" });
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("echoZipCode.zipCode");
    }

    @Test
    void methodConstraintInheritedFromInterface_null() throws Exception {
        ZipCodeServiceImpl service = new ZipCodeServiceImpl();
        Method method = ZipCodeServiceImpl.class.getMethod("echoZipCode", String.class);
        Set<ConstraintViolation<ZipCodeServiceImpl>> violations = execValidator()
                .validateParameters(service, method, new Object[] { null });
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
    }

    // --- Method constraint inheritance from superclass (extends) ---

    static class GreetingService {
        public String greeting(@NotNull String name) {
            return "hello " + name;
        }
    }

    static class EnhancedGreetingService extends GreetingService {
        @Override
        public String greeting(String name) {
            return "Enhanced " + super.greeting(name);
        }
    }

    @Test
    void methodConstraintInheritedFromSuperclass_valid() throws Exception {
        EnhancedGreetingService service = new EnhancedGreetingService();
        Method method = EnhancedGreetingService.class.getMethod("greeting", String.class);
        Set<ConstraintViolation<EnhancedGreetingService>> violations = execValidator()
                .validateParameters(service, method, new Object[] { "World" });
        assertThat(violations).isEmpty();
    }

    @Test
    void methodConstraintInheritedFromSuperclass_violation() throws Exception {
        EnhancedGreetingService service = new EnhancedGreetingService();
        Method method = EnhancedGreetingService.class.getMethod("greeting", String.class);
        Set<ConstraintViolation<EnhancedGreetingService>> violations = execValidator()
                .validateParameters(service, method, new Object[] { null });
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("greeting.name");
    }

    private Validator validator() {
        return container.getValidator();
    }

    private ExecutableValidator execValidator() {
        return validator().forExecutables();
    }
}
