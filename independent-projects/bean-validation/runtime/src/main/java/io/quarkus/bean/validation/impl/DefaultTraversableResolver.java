package io.quarkus.bean.validation.impl;

import java.lang.annotation.ElementType;

import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;

public class DefaultTraversableResolver implements TraversableResolver {

    @Override
    public boolean isReachable(Object traversableObject, Path.Node traversableProperty,
            Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        return true;
    }

    @Override
    public boolean isCascadable(Object traversableObject, Path.Node traversableProperty,
            Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        return true;
    }
}
