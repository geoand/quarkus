package io.quarkus.bean.validation.impl;

import java.util.Map;

/**
 * Shared utility methods for bean validation.
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static final Map<String, Class<?>> PRIMITIVE_TYPES = Map.of(
            "boolean", boolean.class,
            "byte", byte.class,
            "char", char.class,
            "short", short.class,
            "int", int.class,
            "long", long.class,
            "float", float.class,
            "double", double.class,
            "void", void.class);

    /**
     * Converts a getter method name to the corresponding property name.
     * For example, "getName" becomes "name", "isActive" becomes "active".
     * Returns the original method name if it does not match getter conventions.
     */
    public static String getPropertyNameFromGetter(String methodName) {
        String name;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            name = methodName.substring(3);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            name = methodName.substring(2);
        } else {
            return methodName;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Loads a class by name, handling primitive types and using the thread context class loader.
     * Falls back to {@link Class#forName(String)} if the context class loader fails.
     *
     * @return the loaded class, or {@code null} if {@code className} is {@code null}
     * @throws IllegalStateException if the class cannot be found
     */
    public static Class<?> loadClass(String className) {
        if (className == null) {
            return null;
        }
        Class<?> primitive = PRIMITIVE_TYPES.get(className);
        if (primitive != null) {
            return primitive;
        }
        try {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e2) {
                throw new IllegalStateException(
                        "Class not found: " + className
                                + ". Ensure the class is included in the Jandex index.",
                        e2);
            }
        }
    }
}
