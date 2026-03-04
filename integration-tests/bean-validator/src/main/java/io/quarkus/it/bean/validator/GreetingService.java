package io.quarkus.it.bean.validator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.NotNull;

@ApplicationScoped
public class GreetingService {

    public String greet(@NotNull String name) {
        return "Hello, " + name + "!";
    }
}
