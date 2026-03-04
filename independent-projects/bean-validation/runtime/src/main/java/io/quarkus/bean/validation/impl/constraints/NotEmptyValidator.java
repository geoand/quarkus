package io.quarkus.bean.validation.impl.constraints;

import java.util.Collection;
import java.util.Map;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotEmpty;

public class NotEmptyValidator {

    private NotEmptyValidator() {
    }

    public static class ForCharSequence implements ConstraintValidator<NotEmpty, CharSequence> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return !value.isEmpty();
        }
    }

    public static class ForCollection implements ConstraintValidator<NotEmpty, Collection<?>> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return !value.isEmpty();
        }
    }

    public static class ForMap implements ConstraintValidator<NotEmpty, Map<?, ?>> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(Map<?, ?> value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return !value.isEmpty();
        }
    }

    public static class ForArray implements ConstraintValidator<NotEmpty, Object[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(Object[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForBooleanArray implements ConstraintValidator<NotEmpty, boolean[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(boolean[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForByteArray implements ConstraintValidator<NotEmpty, byte[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(byte[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForCharArray implements ConstraintValidator<NotEmpty, char[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(char[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForDoubleArray implements ConstraintValidator<NotEmpty, double[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(double[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForFloatArray implements ConstraintValidator<NotEmpty, float[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(float[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForIntArray implements ConstraintValidator<NotEmpty, int[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(int[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForLongArray implements ConstraintValidator<NotEmpty, long[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(long[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }

    public static class ForShortArray implements ConstraintValidator<NotEmpty, short[]> {

        @Override
        public void initialize(NotEmpty constraintAnnotation) {
        }

        @Override
        public boolean isValid(short[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            return value.length > 0;
        }
    }
}
