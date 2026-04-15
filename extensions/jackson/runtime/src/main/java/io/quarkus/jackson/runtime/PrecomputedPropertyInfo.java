package io.quarkus.jackson.runtime;

import io.quarkus.runtime.annotations.RecordableConstructor;

/**
 * Holds build-time property metadata for a single Jackson property.
 * Passed through the recorder from the deployment module to the runtime.
 */
public class PrecomputedPropertyInfo {

    private final String propertyName;
    private final String declaringClassName;
    private final String fieldName;
    private final String getterName;
    private final String setterName;
    private final String setterParamType;

    @RecordableConstructor
    public PrecomputedPropertyInfo(String propertyName, String declaringClassName, String fieldName,
            String getterName, String setterName, String setterParamType) {
        this.propertyName = propertyName;
        this.declaringClassName = declaringClassName;
        this.fieldName = fieldName;
        this.getterName = getterName;
        this.setterName = setterName;
        this.setterParamType = setterParamType;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getGetterName() {
        return getterName;
    }

    public String getSetterName() {
        return setterName;
    }

    public String getSetterParamType() {
        return setterParamType;
    }
}
