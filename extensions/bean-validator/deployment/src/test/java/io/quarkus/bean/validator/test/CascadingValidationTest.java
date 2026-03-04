package io.quarkus.bean.validator.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class CascadingValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap
                    .create(JavaArchive.class)
                    .addClasses(Order.class, Item.class));

    @Inject
    Validator validator;

    @Test
    void validOrder() {
        Item item = new Item();
        item.name = "Widget";

        Order order = new Order();
        order.customer = "Alice";
        order.items = List.of(item);

        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations).isEmpty();
    }

    @Test
    void cascadingViolation() {
        Item item = new Item();
        // name is null -> violation

        Order order = new Order();
        order.customer = "Alice";
        order.items = List.of(item);

        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("items.name[0]");
    }

    @Test
    void multipleConstraints() {
        Order order = new Order();
        // customer is null, items is null

        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations).hasSize(1); // only @NotNull on customer
    }

    public static class Order {
        @NotNull
        String customer;

        @Valid
        List<Item> items;
    }

    public static class Item {
        @NotNull
        @Size(min = 1, max = 50)
        String name;
    }
}
