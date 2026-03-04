package io.quarkus.bean.validation;

/**
 * Interface for build-time generated accessor classes that extract field
 * and property values without runtime reflection.
 * <p>
 * One implementation is generated per constrained bean class. Private members
 * have their modifier removed at build time, so all constrained members in the
 * same package are accessible via direct field/method access.
 */
public interface BeanPropertyAccessor {

    Object getFieldValue(Object bean, String fieldName);

    /**
     * Invokes the named no-arg method (getter) on the given bean instance
     * and returns the result.
     *
     * @param bean the bean instance
     * @param methodName the getter method name (e.g. "getName")
     * @return the return value of the getter
     * @throws jakarta.validation.ValidationException if the method is not known to this accessor
     */
    Object getPropertyValue(Object bean, String methodName);
}
