package io.quarkus.bean.validator.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.vertx.ext.web.Router;

/**
 * Tests that bean validation works correctly after hot reload in dev mode.
 */
class BeanValidatorDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(DevModeBean.class, ValidationRoute.class));

    @Test
    void validationWorks() {
        // Valid input — no violations
        RestAssured.given()
                .queryParam("name", "Alice")
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(is("valid"));

        // Invalid input — null name triggers @NotNull
        RestAssured.given()
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(containsString("name"));
    }

    @Test
    void hotReloadAddConstraint() {
        // Initially a 3-char name is valid
        RestAssured.given()
                .queryParam("name", "Bob")
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(is("valid"));

        // Add @Size(min=5) constraint via hot reload
        test.modifySourceFile(DevModeBean.class, s -> s.replace(
                "import jakarta.validation.constraints.NotNull;",
                "import jakarta.validation.constraints.NotNull;\nimport jakarta.validation.constraints.Size;")
                .replace("@NotNull", "@NotNull @Size(min = 5)"));

        // Now a 3-char name should be invalid
        RestAssured.given()
                .queryParam("name", "Bob")
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(containsString("name"));

        // A 5-char name should still be valid
        RestAssured.given()
                .queryParam("name", "Alice")
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(is("valid"));
    }

    @Test
    void hotReloadAddField() {
        // Initially only name is validated
        RestAssured.given()
                .queryParam("name", "Alice")
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(is("valid"));

        // Add a new constrained field via hot reload
        test.modifySourceFile(DevModeBean.class, s -> s.replace(
                "import jakarta.validation.constraints.NotNull;",
                "import jakarta.validation.constraints.NotNull;\nimport jakarta.validation.constraints.NotBlank;")
                .replace("}", "    @NotBlank\n    public String email;\n}"));

        // Now validation should fail because email is null
        RestAssured.given()
                .queryParam("name", "Alice")
                .when().get("/validate")
                .then()
                .statusCode(200)
                .body(containsString("email"));
    }

    public static class ValidationRoute {

        @Inject
        Validator validator;

        void init(@Observes Router router) {
            router.get("/validate").handler(rc -> {
                String name = rc.request().getParam("name");
                DevModeBean bean = new DevModeBean();
                bean.name = name;

                Set<ConstraintViolation<DevModeBean>> violations = validator.validate(bean);
                if (violations.isEmpty()) {
                    rc.response().end("valid");
                } else {
                    rc.response().end(violations.stream()
                            .map(v -> v.getPropertyPath().toString())
                            .sorted()
                            .collect(Collectors.joining(",")));
                }
            });
        }
    }
}
