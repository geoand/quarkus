package io.quarkus.bean.validator.test.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import jakarta.validation.constraints.Digits;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;

class RestEndPointValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap
                    .create(JavaArchive.class)
                    .addClasses(TestResource.class));

    @Test
    void validParameterReturns200() {
        given()
                .when().get("/test/validate/42")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
    }

    @Test
    void invalidParameterReturns400WithJsonViolationReport() {
        String body = given()
                .accept(ContentType.JSON)
                .when().get("/test/validate/invalid")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .extract().body().asString();

        assertThat(body)
                .contains("Constraint Violation")
                .contains("\"status\":400")
                .contains("\"violations\"")
                .contains("numeric value out of bounds");
    }

    @Test
    void invalidParameterWithNoAcceptReturns400() {
        given()
                .when().get("/test/validate/invalid")
                .then()
                .statusCode(400)
                .body(containsString("numeric value out of bounds"));
    }

    @Test
    void returnValueViolationReturns500() {
        given()
                .when().get("/test/return-value/invalid")
                .then()
                .statusCode(500);
    }

    @Test
    void validReturnValueReturns200() {
        given()
                .when().get("/test/return-value/42")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
    }

    @Test
    void noProducesStillReturns400() {
        given()
                .when().get("/test/no-produces/invalid")
                .then()
                .statusCode(400)
                .body(containsString("numeric value out of bounds"));
    }

    @Path("/test")
    public static class TestResource {

        @GET
        @Path("/validate/{id}")
        @Produces(MediaType.APPLICATION_JSON)
        public String validate(@Digits(integer = 5, fraction = 0) @RestPath String id) {
            return id;
        }

        @GET
        @Path("/return-value/{returnValue}")
        @Produces(MediaType.APPLICATION_JSON)
        @Digits(integer = 5, fraction = 0)
        public String returnValueValidation(@RestPath String returnValue) {
            return returnValue;
        }

        @GET
        @Path("/no-produces/{id}")
        public String noProduces(@Digits(integer = 5, fraction = 0) @RestPath String id) {
            return id;
        }
    }
}
