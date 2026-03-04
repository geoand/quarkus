package io.quarkus.it.bean.validator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestPath;

import io.quarkus.it.bean.validator.custom.MyOtherBean;

@Path("/bean-validator")
public class BeanValidatorTestResource {

    @Inject
    Validator validator;

    @Inject
    ValidatorFactory validatorFactory;

    @Inject
    GreetingService greetingService;

    @GET
    @Path("/validate-valid")
    @Produces(MediaType.TEXT_PLAIN)
    public String validateValid() {
        TestBean bean = new TestBean();
        bean.name = "Alice";
        bean.email = "alice@example.com";
        bean.age = 30;

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        return violations.isEmpty() ? "valid" : formatViolations(violations);
    }

    @GET
    @Path("/validate-invalid")
    @Produces(MediaType.TEXT_PLAIN)
    public String validateInvalid() {
        TestBean bean = new TestBean();
        // name is null, email is null, age is 0 (valid for @Min(0))

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        return violations.size() + ":" + formatViolations(violations);
    }

    @GET
    @Path("/validate-cascading")
    @Produces(MediaType.TEXT_PLAIN)
    public String validateCascading() {
        TestBean bean = new TestBean();
        bean.name = "Alice";
        bean.email = "alice@example.com";
        bean.age = 25;

        TestBean.Address address = new TestBean.Address();
        // street, city, zipCode all null -> violations
        bean.address = address;

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        return violations.size() + ":" + formatViolations(violations);
    }

    @GET
    @Path("/validate-size")
    @Produces(MediaType.TEXT_PLAIN)
    public String validateSize(@QueryParam("name") String name) {
        TestBean bean = new TestBean();
        bean.name = name;
        bean.email = "test@test.com";
        bean.age = 20;

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        return violations.isEmpty() ? "valid" : formatViolations(violations);
    }

    @GET
    @Path("/validate-min-max")
    @Produces(MediaType.TEXT_PLAIN)
    public String validateMinMax(@QueryParam("age") int age) {
        TestBean bean = new TestBean();
        bean.name = "Alice";
        bean.email = "test@test.com";
        bean.age = age;

        Set<ConstraintViolation<TestBean>> violations = validator.validate(bean);
        return violations.isEmpty() ? "valid" : formatViolations(violations);
    }

    @GET
    @Path("/validator-factory")
    @Produces(MediaType.TEXT_PLAIN)
    public String validatorFactory() {
        return validatorFactory != null ? "present" : "absent";
    }

    @GET
    @Path("/basic-features")
    @Produces(MediaType.TEXT_PLAIN)
    public String testBasicFeatures() {
        StringBuilder result = new StringBuilder();

        Map<String, List<String>> invalidCategorizedEmails = new HashMap<>();
        invalidCategorizedEmails.put("a", Collections.singletonList("b"));

        result.append(formatViolations(validator.validate(new MyBean(
                "Bill Jones",
                "b",
                Collections.singletonList("c"),
                -4d,
                invalidCategorizedEmails))));

        Map<String, List<String>> validCategorizedEmails = new HashMap<>();
        validCategorizedEmails.put("Professional", Collections.singletonList("bill.jones@example.com"));

        result.append("\n");
        result.append(formatViolations(validator.validate(new MyBean(
                "Bill Jones",
                "bill.jones@example.com",
                Collections.singletonList("biji@example.com"),
                5d,
                validCategorizedEmails))));

        return result.toString();
    }

    @GET
    @Path("/custom-constraint")
    @Produces(MediaType.TEXT_PLAIN)
    public String testCustomConstraint() {
        Set<ConstraintViolation<MyOtherBean>> invalidViolations = validator.validate(new MyOtherBean(null));
        Set<ConstraintViolation<MyOtherBean>> validViolations = validator.validate(new MyOtherBean("test"));
        return invalidViolations.size() + ":" + validViolations.size();
    }

    @GET
    @Path("/cdi-bean-method-validation")
    @Produces(MediaType.TEXT_PLAIN)
    public String testCDIBeanMethodValidation() {
        try {
            greetingService.greet(null);
            return "no-violation";
        } catch (ConstraintViolationException e) {
            return "violation:" + e.getConstraintViolations().size();
        }
    }

    @GET
    @Path("/rest-end-point-validation/{id}/")
    @Produces(MediaType.TEXT_PLAIN)
    public String testRestEndPointValidation(@Digits(integer = 5, fraction = 0) @RestPath("id") String id) {
        return id;
    }

    @GET
    @Path("/rest-end-point-return-value-validation/{returnValue}/")
    @Produces(MediaType.TEXT_PLAIN)
    @Digits(integer = 5, fraction = 0)
    public String testRestEndPointReturnValueValidation(@RestPath("returnValue") String returnValue) {
        return returnValue;
    }

    private <T> String formatViolations(Set<ConstraintViolation<T>> violations) {
        if (violations.isEmpty()) {
            return "passed";
        }
        return violations.stream()
                .map(v -> v.getPropertyPath() + ":" + v.getMessage())
                .sorted()
                .collect(Collectors.joining(","));
    }

    public static class MyBean {

        @Email
        private String email;

        private List<@Email String> additionalEmails;

        @DecimalMin("0")
        private Double score;

        private Map<@Size(min = 3) String, List<@Email String>> categorizedEmails;

        public MyBean(String name, String email, List<String> additionalEmails, Double score,
                Map<String, List<String>> categorizedEmails) {
            this.email = email;
            this.additionalEmails = additionalEmails;
            this.score = score;
            this.categorizedEmails = categorizedEmails;
        }
    }
}
