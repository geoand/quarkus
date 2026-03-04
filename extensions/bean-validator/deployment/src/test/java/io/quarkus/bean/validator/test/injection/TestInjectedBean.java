package io.quarkus.bean.validator.test.injection;

import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestInjectedBean {

    public List<String> allowedStrings() {
        return Collections.singletonList("Alpha");
    }

}
