package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CascadingValidationTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(Order.class, OrderItem.class, Address.class)
            .build();

    static class Address {
        @NotNull
        String street;
        @NotNull
        String city;
    }

    static class OrderItem {
        @NotNull
        String name;
        @jakarta.validation.constraints.Positive
        int quantity;
    }

    static class Order {
        @NotNull
        String orderId;

        @Valid
        @NotNull
        Address address;

        @Valid
        @Size(min = 1)
        List<OrderItem> items;
    }

    @Test
    void validCascading() {
        Order order = new Order();
        order.orderId = "ORD-1";
        order.address = new Address();
        order.address.street = "123 Main St";
        order.address.city = "Springfield";
        OrderItem item = new OrderItem();
        item.name = "Widget";
        item.quantity = 5;
        order.items = Arrays.asList(item);

        assertThat(validator().validate(order)).isEmpty();
    }

    @Test
    void cascadingIntoObject() {
        Order order = new Order();
        order.orderId = "ORD-1";
        order.address = new Address(); // street and city are null
        OrderItem item = new OrderItem();
        item.name = "Widget";
        item.quantity = 5;
        order.items = Arrays.asList(item);

        Set<ConstraintViolation<Order>> violations = validator().validate(order);
        assertThat(violations).hasSize(2); // address.street and address.city
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("address.street", "address.city");
    }

    @Test
    void cascadingIntoList() {
        Order order = new Order();
        order.orderId = "ORD-1";
        order.address = new Address();
        order.address.street = "123 Main St";
        order.address.city = "Springfield";
        OrderItem invalidItem = new OrderItem();
        invalidItem.name = null; // violation
        invalidItem.quantity = 0; // violation
        order.items = Arrays.asList(invalidItem);

        Set<ConstraintViolation<Order>> violations = validator().validate(order);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void nullCascadedObjectIsNotValidated() {
        Order order = new Order();
        order.orderId = "ORD-1";
        order.address = null; // @Valid but null - only @NotNull triggers, no cascading
        order.items = Arrays.asList();

        Set<ConstraintViolation<Order>> violations = validator().validate(order);
        // Should have violation for @NotNull on address, but not for address.street/city
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("address");
    }

    private Validator validator() {
        return container.getValidator();
    }
}
