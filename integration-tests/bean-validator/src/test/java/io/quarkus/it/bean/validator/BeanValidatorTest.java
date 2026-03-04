package io.quarkus.it.bean.validator;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class BeanValidatorTest {

    @Test
    public void testValidBean() {
        given()
                .when().get("/bean-validator/validate-valid")
                .then()
                .statusCode(200)
                .body(is("valid"));
    }

    @Test
    public void testInvalidBean() {
        given()
                .when().get("/bean-validator/validate-invalid")
                .then()
                .statusCode(200)
                .body(startsWith("2:"));
    }

    @Test
    public void testCascadingValidation() {
        given()
                .when().get("/bean-validator/validate-cascading")
                .then()
                .statusCode(200)
                .body(startsWith("3:"))
                .body(containsString("address.street"))
                .body(containsString("address.city"))
                .body(containsString("address.zipCode"));
    }

    @Test
    public void testSizeValid() {
        given()
                .queryParam("name", "Alice")
                .when().get("/bean-validator/validate-size")
                .then()
                .statusCode(200)
                .body(is("valid"));
    }

    @Test
    public void testSizeTooLong() {
        given()
                .queryParam("name", "A".repeat(101))
                .when().get("/bean-validator/validate-size")
                .then()
                .statusCode(200)
                .body(containsString("name"));
    }

    @Test
    public void testMinMaxValid() {
        given()
                .queryParam("age", 30)
                .when().get("/bean-validator/validate-min-max")
                .then()
                .statusCode(200)
                .body(is("valid"));
    }

    @Test
    public void testMinMaxInvalid() {
        given()
                .queryParam("age", -1)
                .when().get("/bean-validator/validate-min-max")
                .then()
                .statusCode(200)
                .body(containsString("age"));
    }

    @Test
    public void testMaxInvalid() {
        given()
                .queryParam("age", 200)
                .when().get("/bean-validator/validate-min-max")
                .then()
                .statusCode(200)
                .body(containsString("age"));
    }

    @Test
    public void testValidatorFactoryInjected() {
        given()
                .when().get("/bean-validator/validator-factory")
                .then()
                .statusCode(200)
                .body(is("present"));
    }

    @Test
    public void testBasicFeatures() {
        RestAssured.when()
                .get("/bean-validator/basic-features")
                .then()
                .statusCode(200)
                .body(containsString("email:must be a well-formed email address"))
                .body(containsString("passed"));
    }

    @Test
    public void testCustomConstraint() {
        // 1 violation for null name, 0 violations for valid name
        given()
                .when().get("/bean-validator/custom-constraint")
                .then()
                .statusCode(200)
                .body(is("1:0"));
    }

    @Test
    public void testCDIBeanMethodValidation() {
        given()
                .when().get("/bean-validator/cdi-bean-method-validation")
                .then()
                .statusCode(200)
                .body(is("violation:1"));
    }

    @Test
    public void testRestEndPointValidation() {
        // Valid numeric input
        RestAssured.when()
                .get("/bean-validator/rest-end-point-validation/42/")
                .then()
                .statusCode(200)
                .body(is("42"));
    }

    @Test
    public void testRestEndPointValidationInvalid() {
        // Invalid non-numeric input is a client error (bad request), not a server error
        RestAssured.when()
                .get("/bean-validator/rest-end-point-validation/plop/")
                .then()
                .statusCode(400)
                .body(containsString("numeric value out of bounds"));
    }

    @Test
    public void testRestEndPointReturnValueValidation() {
        // Valid numeric return value
        RestAssured.when()
                .get("/bean-validator/rest-end-point-return-value-validation/42/")
                .then()
                .statusCode(200)
                .body(is("42"));
    }

    @Test
    public void testRestEndPointReturnValueValidationInvalid() {
        // Invalid non-numeric return value should be an internal error
        RestAssured.when()
                .get("/bean-validator/rest-end-point-return-value-validation/plop/")
                .then()
                .statusCode(500);
    }
}
