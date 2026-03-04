package io.quarkus.bean.validation.impl;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jakarta.validation.ValidationException;

/**
 * Shared utility methods for annotation value coercion, equality, and hashing.
 * Used by both {@link AnnotationProxy} (dynamic proxy fallback) and
 * {@link AbstractConstraintAnnotationLiteral} (build-time generated literals).
 */
public final class AnnotationUtils {

    private AnnotationUtils() {
    }

    /**
     * Coerces a raw attribute value (potentially serialized as String/List) to the expected
     * annotation member type.
     */
    @SuppressWarnings("unchecked")
    public static Object coerceValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // Already the right type (including Class, Class[], enum, etc. passed directly)
        if (targetType.isInstance(value)) {
            return value;
        }

        // Handle Class values stored as strings
        if (targetType == Class.class && value instanceof String) {
            try {
                return Class.forName((String) value, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ValidationException("Cannot load class: " + value, e);
            }
        }

        // Handle Class[] values stored as List<String>
        if (targetType == Class[].class && value instanceof List) {
            List<String> classNames = (List<String>) value;
            Class<?>[] classes = new Class<?>[classNames.size()];
            for (int i = 0; i < classNames.size(); i++) {
                try {
                    classes[i] = Class.forName(classNames.get(i), false,
                            Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new ValidationException("Cannot load class: " + classNames.get(i), e);
                }
            }
            return classes;
        }

        // Handle enum values stored as strings
        if (targetType.isEnum() && value instanceof String) {
            return Enum.valueOf((Class<Enum>) targetType, (String) value);
        }

        // Handle enum arrays stored as List<String>
        if (targetType.isArray() && targetType.getComponentType().isEnum() && value instanceof List) {
            List<String> enumNames = (List<String>) value;
            Object array = Array.newInstance(targetType.getComponentType(), enumNames.size());
            for (int i = 0; i < enumNames.size(); i++) {
                Array.set(array, i, Enum.valueOf((Class<Enum>) targetType.getComponentType(), enumNames.get(i)));
            }
            return array;
        }

        // Handle numeric type conversions
        if (value instanceof Number num) {
            if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            }
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            }
            if (targetType == short.class || targetType == Short.class) {
                return num.shortValue();
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return num.byteValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            }
        }

        // Handle String[] from List<String>
        if (targetType == String[].class && value instanceof List) {
            List<String> list = (List<String>) value;
            return list.toArray(new String[0]);
        }

        return value;
    }

    /**
     * Computes the hash code of an annotation member value, handling arrays correctly
     * per the {@link java.lang.annotation.Annotation#hashCode()} contract.
     */
    public static int memberHashCode(Object value) {
        if (value == null) {
            return 0;
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return value.hashCode();
        }
        if (type == byte[].class) {
            return Arrays.hashCode((byte[]) value);
        } else if (type == char[].class) {
            return Arrays.hashCode((char[]) value);
        } else if (type == double[].class) {
            return Arrays.hashCode((double[]) value);
        } else if (type == float[].class) {
            return Arrays.hashCode((float[]) value);
        } else if (type == int[].class) {
            return Arrays.hashCode((int[]) value);
        } else if (type == long[].class) {
            return Arrays.hashCode((long[]) value);
        } else if (type == short[].class) {
            return Arrays.hashCode((short[]) value);
        } else if (type == boolean[].class) {
            return Arrays.hashCode((boolean[]) value);
        } else {
            return Arrays.hashCode((Object[]) value);
        }
    }

    /**
     * Compares two annotation member values for equality, handling arrays correctly
     * per the {@link java.lang.annotation.Annotation#equals(Object)} contract.
     */
    public static boolean memberEquals(Object a, Object b) {
        return Objects.deepEquals(a, b);
    }

    public static String arrayToString(Object array) {
        Class<?> type = array.getClass();
        if (type == byte[].class) {
            return Arrays.toString((byte[]) array);
        } else if (type == char[].class) {
            return Arrays.toString((char[]) array);
        } else if (type == double[].class) {
            return Arrays.toString((double[]) array);
        } else if (type == float[].class) {
            return Arrays.toString((float[]) array);
        } else if (type == int[].class) {
            return Arrays.toString((int[]) array);
        } else if (type == long[].class) {
            return Arrays.toString((long[]) array);
        } else if (type == short[].class) {
            return Arrays.toString((short[]) array);
        } else if (type == boolean[].class) {
            return Arrays.toString((boolean[]) array);
        } else {
            return Arrays.toString((Object[]) array);
        }
    }
}
