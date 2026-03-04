package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.QuarkusValidatorFactory;

class ValidationProviderTest {

    @Test
    void buildDefaultValidatorFactory() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory).isInstanceOf(QuarkusValidatorFactory.class);
            Validator validator = factory.getValidator();
            assertThat(validator).isNotNull();
        }
    }

    @Test
    void buildByProvider() {
        try (ValidatorFactory factory = Validation.byProvider(QuarkusValidationProvider.class)
                .configure()
                .buildValidatorFactory()) {
            assertThat(factory).isInstanceOf(QuarkusValidatorFactory.class);
        }
    }

    @Test
    void validatorFromContext() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.usingContext().getValidator();
            assertThat(validator).isNotNull();
        }
    }

    @Test
    void getMessageInterpolator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getMessageInterpolator()).isNotNull();
        }
    }

    @Test
    void getClockProvider() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getClockProvider()).isNotNull();
            assertThat(factory.getClockProvider().getClock()).isNotNull();
        }
    }

    @Test
    void unwrapValidatorFactory() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            QuarkusValidatorFactory qvf = factory.unwrap(QuarkusValidatorFactory.class);
            assertThat(qvf).isSameAs(factory);
        }
    }
}
