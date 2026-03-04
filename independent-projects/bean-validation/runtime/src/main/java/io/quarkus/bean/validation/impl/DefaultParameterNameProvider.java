package io.quarkus.bean.validation.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.ParameterNameProvider;

public class DefaultParameterNameProvider implements ParameterNameProvider {

    @Override
    public List<String> getParameterNames(Constructor<?> constructor) {
        return extractParameterNames(constructor.getParameters());
    }

    @Override
    public List<String> getParameterNames(Method method) {
        return extractParameterNames(method.getParameters());
    }

    private List<String> extractParameterNames(Parameter[] parameters) {
        List<String> names = new ArrayList<>(parameters.length);
        for (Parameter parameter : parameters) {
            names.add(parameter.getName());
        }
        return names;
    }
}
