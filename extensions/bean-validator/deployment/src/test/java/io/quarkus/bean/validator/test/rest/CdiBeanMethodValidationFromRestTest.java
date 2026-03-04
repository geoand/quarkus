package io.quarkus.bean.validator.test.rest;

import static io.restassured.RestAssured.given;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that a ConstraintViolationException from a CDI bean method (not a REST endpoint)
 * results in HTTP 500, not 400. This validates that @MethodValidated and @JaxrsEndPointValidated
 * are correctly distinguished.
 */
class CdiBeanMethodValidationFromRestTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap
                    .create(JavaArchive.class)
                    .addClasses(TestResource.class, GreetingService.class));

    @Test
    void cdiBeanMethodValidationFailureReturns500() {
        // The REST endpoint itself has no validation constraints,
        // but it calls a CDI bean method that has @NotNull validation.
        // This should result in 500 (internal error), not 400 (client error).
        given()
                .when().get("/test/cdi-validation")
                .then()
                .statusCode(500);
    }

    @Path("/test")
    public static class TestResource {

        @Inject
        GreetingService greetingService;

        @GET
        @Path("/cdi-validation")
        @Produces(MediaType.TEXT_PLAIN)
        public String callCdiBean() {
            return greetingService.greet(null);
        }
    }

    @ApplicationScoped
    public static class GreetingService {
        public String greet(@NotNull String name) {
            return "Hello " + name;
        }
    }
}
