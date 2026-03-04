package io.quarkus.bean.validation.impl.constraints;

import java.util.Collection;
import java.util.Map;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Size;

public class SizeValidator {

    private SizeValidator() {
    }

    public static class ForCharSequence implements ConstraintValidator<Size, CharSequence> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length();
            return length >= min && length <= max;
        }
    }

    public static class ForCollection implements ConstraintValidator<Size, Collection<?>> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int size = value.size();
            return size >= min && size <= max;
        }
    }

    public static class ForMap implements ConstraintValidator<Size, Map<?, ?>> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(Map<?, ?> value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int size = value.size();
            return size >= min && size <= max;
        }
    }

    public static class ForArray implements ConstraintValidator<Size, Object[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(Object[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForBooleanArray implements ConstraintValidator<Size, boolean[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(boolean[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForByteArray implements ConstraintValidator<Size, byte[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(byte[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForCharArray implements ConstraintValidator<Size, char[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(char[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForDoubleArray implements ConstraintValidator<Size, double[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(double[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForFloatArray implements ConstraintValidator<Size, float[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(float[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForIntArray implements ConstraintValidator<Size, int[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(int[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForLongArray implements ConstraintValidator<Size, long[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(long[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }

    public static class ForShortArray implements ConstraintValidator<Size, short[]> {

        private int min;
        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.min = constraintAnnotation.min();
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(short[] value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int length = value.length;
            return length >= min && length <= max;
        }
    }
}
