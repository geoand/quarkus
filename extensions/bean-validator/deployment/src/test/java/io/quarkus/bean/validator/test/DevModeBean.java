package io.quarkus.bean.validator.test;

import jakarta.validation.constraints.NotNull;

public class DevModeBean {

    @NotNull
    public String name;
}
