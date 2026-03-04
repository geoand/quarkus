/*
 * Test utility for creating constraint annotation instances via AnnotationProxy.
 * Adapted from Hibernate Validator's ConstraintAnnotationDescriptor.
 */
package io.quarkus.bean.validation.test.constraints;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.bean.validation.impl.AnnotationProxy;

final class ConstraintAnnotationHelper {

    private ConstraintAnnotationHelper() {
    }

    static <A extends Annotation> A annotation(Class<A> annotationType) {
        return AnnotationProxy.create(annotationType, Map.of());
    }

    static <A extends Annotation> A annotation(Class<A> annotationType, String key, Object value) {
        return AnnotationProxy.create(annotationType, Map.of(key, value));
    }

    static <A extends Annotation> A annotation(Class<A> annotationType, Map<String, Object> attributes) {
        return AnnotationProxy.create(annotationType, attributes);
    }

    static Map<String, Object> attrs(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
