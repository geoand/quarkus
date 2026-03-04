package io.quarkus.bean.validation.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Creates dynamic proxies for constraint annotation instances using attribute maps
 * from build-time metadata. Used as a fallback when no build-time generated literal
 * class is available.
 */
public final class AnnotationProxy {

    private AnnotationProxy() {
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A create(Class<A> annotationType, Map<String, Object> attributes) {
        return (A) Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[] { annotationType },
                new AnnotationInvocationHandler(annotationType, attributes));
    }

    private static class AnnotationInvocationHandler implements InvocationHandler {
        private final Class<? extends Annotation> annotationType;
        private final Map<String, Object> attributes;

        AnnotationInvocationHandler(Class<? extends Annotation> annotationType, Map<String, Object> attributes) {
            this.annotationType = annotationType;
            this.attributes = attributes;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();

            if ("annotationType".equals(name) && method.getParameterCount() == 0) {
                return annotationType;
            }
            if ("toString".equals(name) && method.getParameterCount() == 0) {
                return toStringImpl();
            }
            if ("hashCode".equals(name) && method.getParameterCount() == 0) {
                return hashCodeImpl();
            }
            if ("equals".equals(name) && method.getParameterCount() == 1) {
                return equalsImpl(args[0]);
            }

            // Attribute access
            Object value = attributes.get(name);
            if (value != null) {
                return AnnotationUtils.coerceValue(value, method.getReturnType());
            }

            // Fall back to annotation default value
            return method.getDefaultValue();
        }

        private String toStringImpl() {
            StringBuilder sb = new StringBuilder();
            sb.append('@').append(annotationType.getName()).append('(');
            boolean first = true;
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append('=');
                Object v = entry.getValue();
                if (v != null && v.getClass().isArray()) {
                    sb.append(AnnotationUtils.arrayToString(v));
                } else {
                    sb.append(v);
                }
                first = false;
            }
            sb.append(')');
            return sb.toString();
        }

        private int hashCodeImpl() {
            int result = 0;
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                int nameHash = entry.getKey().hashCode() * 127;
                int valueHash = AnnotationUtils.memberHashCode(entry.getValue());
                result += nameHash ^ valueHash;
            }
            return result;
        }

        private boolean equalsImpl(Object other) {
            if (!annotationType.isInstance(other)) {
                return false;
            }
            for (Method method : annotationType.getDeclaredMethods()) {
                String name = method.getName();
                if (method.getParameterCount() != 0) {
                    continue;
                }
                try {
                    Object thisValue = invoke(null, method, null);
                    Object otherValue = method.invoke(other);
                    if (!AnnotationUtils.memberEquals(thisValue, otherValue)) {
                        return false;
                    }
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            }
            return true;
        }
    }
}
