package io.quarkus.jackson.runtime;

import java.util.List;

/**
 * Holds all build-time Jackson metadata for a single class.
 * Passed through the recorder from the deployment module to the runtime.
 */
public record PrecomputedClassMetadata(List<PrecomputedPropertyInfo> properties,
        List<String> creatorConstructorParamTypes,
        List<String> creatorConstructorParamNames,
        String creatorFactoryMethodName,
        String creatorFactoryMethodDeclaringClass,
        List<String> creatorFactoryMethodParamTypes,
        List<String> creatorFactoryMethodParamNames,
        String jsonValueMethodName,
        String jsonValueFieldName,
        String jsonValueFieldDeclaringClass,
        String anyGetterMethodName,
        String anySetterMethodName,
        String anySetterParamType,
        List<String> ignoredProperties) {
}
