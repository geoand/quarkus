package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CustomConstraintTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(ProductBean.class)
            .build();

    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Constraint(validatedBy = ValidProductCode.Validator.class)
    @interface ValidProductCode {
        String message() default "Invalid product code";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        class Validator implements ConstraintValidator<ValidProductCode, String> {
            @Override
            public boolean isValid(String value, ConstraintValidatorContext context) {
                if (value == null) {
                    return true;
                }
                // Product code must start with "PRD-" followed by digits
                return value.matches("PRD-\\d+");
            }
        }
    }

    static class ProductBean {
        @ValidProductCode
        String code;
    }

    @Test
    void customConstraintValid() {
        ProductBean bean = new ProductBean();
        bean.code = "PRD-12345";
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void customConstraintNullIsValid() {
        ProductBean bean = new ProductBean();
        bean.code = null;
        assertThat(validator().validate(bean)).isEmpty();
    }

    @Test
    void customConstraintInvalid() {
        ProductBean bean = new ProductBean();
        bean.code = "INVALID-CODE";
        Set<ConstraintViolation<ProductBean>> violations = validator().validate(bean);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Invalid product code");
    }

    private Validator validator() {
        return container.getValidator();
    }
}
