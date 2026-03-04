package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupSequence;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GroupValidationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(UserRegistration.class, SequencedBean.class)
            .build();

    interface BasicChecks {
    }

    interface AdvancedChecks {
    }

    static class UserRegistration {
        @NotNull(groups = BasicChecks.class)
        String username;

        @NotNull(groups = BasicChecks.class)
        @Size(min = 8, groups = AdvancedChecks.class)
        String password;

        @NotNull
        String email;
    }

    @Test
    void defaultGroupOnly() {
        UserRegistration user = new UserRegistration();
        user.username = "john";
        user.password = "short";
        user.email = null;

        Set<ConstraintViolation<UserRegistration>> violations = validator().validate(user);
        // Only Default group - email is @NotNull in Default
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
    }

    @Test
    void specificGroup() {
        UserRegistration user = new UserRegistration();
        user.username = null;
        user.password = null;
        user.email = "test@example.com";

        Set<ConstraintViolation<UserRegistration>> violations = validator().validate(user, BasicChecks.class);
        // BasicChecks group - username and password are @NotNull
        assertThat(violations).hasSize(2);
    }

    @Test
    void multipleGroups() {
        UserRegistration user = new UserRegistration();
        user.username = "john";
        user.password = "short"; // not null but too short
        user.email = "test@example.com";

        Set<ConstraintViolation<UserRegistration>> violations = validator().validate(user, BasicChecks.class,
                AdvancedChecks.class);
        // password is too short for AdvancedChecks
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("password");
    }

    @GroupSequence({ BasicSeq.class, AdvancedSeq.class, SequencedBean.class })
    static class SequencedBean {
        @NotNull(groups = BasicSeq.class)
        String name;

        @Size(min = 5, groups = AdvancedSeq.class)
        String description;
    }

    interface BasicSeq {
    }

    interface AdvancedSeq {
    }

    @Test
    void groupSequenceStopsOnFirstFailure() {
        SequencedBean bean = new SequencedBean();
        bean.name = null; // fails BasicSeq
        bean.description = "ab"; // would fail AdvancedSeq but shouldn't be checked

        Set<ConstraintViolation<SequencedBean>> violations = validator().validate(bean);
        // Only BasicSeq violations should appear due to sequence
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
    }

    @Test
    void groupSequenceAllPass() {
        SequencedBean bean = new SequencedBean();
        bean.name = "hello";
        bean.description = "long enough";

        assertThat(validator().validate(bean)).isEmpty();
    }

    private Validator validator() {
        return container.getValidator();
    }
}
