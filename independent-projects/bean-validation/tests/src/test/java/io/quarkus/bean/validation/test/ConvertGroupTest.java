package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests {@code @ConvertGroup} for group conversion during cascading validation.
 */
class ConvertGroupTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(OrderForm.class, OrderItem.class, OrderItemList.class)
            .build();

    // --- Validation groups ---

    interface PostGroup {
    }

    interface PutGroup {
    }

    interface GetGroup {
    }

    // --- Beans ---

    static class OrderItem {
        @Null(groups = PostGroup.class, message = "id must be null for creation")
        @NotNull(groups = PutGroup.class, message = "id required for update")
        Long id;

        @NotNull
        String name;

        @AssertFalse(groups = GetGroup.class, message = "must not be deleted for get")
        @AssertTrue(groups = PostGroup.class, message = "must be marked for post")
        Boolean active;

        OrderItem(Long id, String name, Boolean active) {
            this.id = id;
            this.name = name;
            this.active = active;
        }
    }

    static class OrderForm {
        @Valid
        @ConvertGroup(from = Default.class, to = PostGroup.class)
        OrderItem createItem;

        @Valid
        @ConvertGroup(from = Default.class, to = PutGroup.class)
        OrderItem updateItem;

        @Valid
        @ConvertGroup(from = Default.class, to = GetGroup.class)
        OrderItem viewItem;
    }

    @Test
    void convertGroupForPost_idMustBeNull() {
        OrderForm form = new OrderForm();
        form.createItem = new OrderItem(null, "Widget", true);
        // PostGroup: id=null is valid, active=true satisfies @AssertTrue
        assertThat(validator().validate(form)).isEmpty();
    }

    @Test
    void convertGroupForPost_idNotNullViolation() {
        OrderForm form = new OrderForm();
        form.createItem = new OrderItem(1L, "Widget", true);
        // PostGroup: id=1L violates @Null
        Set<ConstraintViolation<OrderForm>> violations = validator().validate(form);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("id must be null for creation");
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("createItem.id");
    }

    @Test
    void convertGroupForPut_idRequired() {
        OrderForm form = new OrderForm();
        form.updateItem = new OrderItem(1L, "Widget", false);
        // PutGroup: id=1L satisfies @NotNull
        assertThat(validator().validate(form)).isEmpty();
    }

    @Test
    void convertGroupForPut_idNullViolation() {
        OrderForm form = new OrderForm();
        form.updateItem = new OrderItem(null, "Widget", false);
        // PutGroup: id=null violates @NotNull
        Set<ConstraintViolation<OrderForm>> violations = validator().validate(form);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("id required for update");
    }

    @Test
    void convertGroupForGet_deletedFlagCheck() {
        OrderForm form = new OrderForm();
        form.viewItem = new OrderItem(1L, "Widget", false);
        // GetGroup: active=false satisfies @AssertFalse
        assertThat(validator().validate(form)).isEmpty();
    }

    @Test
    void convertGroupForGet_deletedViolation() {
        OrderForm form = new OrderForm();
        form.viewItem = new OrderItem(1L, "Widget", true);
        // GetGroup: active=true violates @AssertFalse
        Set<ConstraintViolation<OrderForm>> violations = validator().validate(form);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be deleted for get");
    }

    @Test
    void convertGroupDefaultConstraintsNotApplied() {
        // Constraints without explicit group (Default group) should NOT be applied
        // when @ConvertGroup converts to a specific group
        OrderForm form = new OrderForm();
        form.createItem = new OrderItem(null, null, true);
        // name=null would violate @NotNull (Default group), but @ConvertGroup converts
        // Default to PostGroup, so only PostGroup constraints are checked
        Set<ConstraintViolation<OrderForm>> violations = validator().validate(form);
        // Only PostGroup constraints: @Null(id) ok, @AssertTrue(active) ok
        // @NotNull(name) is Default group, not PostGroup - so NOT checked
        assertThat(violations).isEmpty();
    }

    // --- @ConvertGroup on List elements ---

    static class OrderItemList {
        @Valid
        @ConvertGroup(from = Default.class, to = PostGroup.class)
        List<OrderItem> items;
    }

    @Test
    void convertGroupOnListCascading() {
        OrderItemList list = new OrderItemList();
        list.items = Arrays.asList(
                new OrderItem(null, "A", true), // valid for PostGroup
                new OrderItem(5L, "B", true) // id=5L violates @Null in PostGroup
        );
        Set<ConstraintViolation<OrderItemList>> violations = validator().validate(list);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("items.id[1]");
    }

    private Validator validator() {
        return container.getValidator();
    }
}
