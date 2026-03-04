package io.quarkus.bean.validator.test.devmode;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import io.vertx.ext.web.Router;

public class DevModeValidationRoute {

    @Inject
    Validator validator;

    @Inject
    DependentTestBean dependentTestBean;

    void init(@Observes Router router) {
        router.post("/test/validate").handler(rc -> {
            TestBean bean = new TestBean();
            Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
            if (violations.isEmpty()) {
                rc.response().end("ok");
            } else {
                rc.response().end(violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .sorted()
                        .collect(Collectors.joining(",")));
            }
        });

        router.get("/test/:message").handler(rc -> {
            String message = rc.pathParam("message");
            try {
                String result = dependentTestBean.testMethod(message);
                rc.response().end(result);
            } catch (ConstraintViolationException e) {
                rc.response().end(e.getConstraintViolations().stream()
                        .map(ConstraintViolation::getMessage)
                        .sorted()
                        .collect(Collectors.joining(",")));
            }
        });
    }
}
