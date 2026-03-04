package io.quarkus.bean.validation.impl.constraints;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Min;

public class MinValidator {

    private MinValidator() {
    }

    public static class ForNumber implements ConstraintValidator<Min, Number> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (value instanceof BigDecimal bd) {
                return bd.compareTo(BigDecimal.valueOf(minValue)) >= 0;
            }
            if (value instanceof BigInteger bi) {
                return bi.compareTo(BigInteger.valueOf(minValue)) >= 0;
            }
            if (value instanceof Double || value instanceof Float) {
                double d = value.doubleValue();
                if (Double.isNaN(d)) {
                    return false;
                }
                return d >= minValue;
            }
            return value.longValue() >= minValue;
        }
    }

    public static class ForBigDecimal implements ConstraintValidator<Min, BigDecimal> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.compareTo(BigDecimal.valueOf(minValue)) >= 0;
        }
    }

    public static class ForBigInteger implements ConstraintValidator<Min, BigInteger> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(BigInteger value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.compareTo(BigInteger.valueOf(minValue)) >= 0;
        }
    }

    public static class ForLong implements ConstraintValidator<Min, Long> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value >= minValue;
        }
    }

    public static class ForInteger implements ConstraintValidator<Min, Integer> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.longValue() >= minValue;
        }
    }

    public static class ForShort implements ConstraintValidator<Min, Short> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Short value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.longValue() >= minValue;
        }
    }

    public static class ForByte implements ConstraintValidator<Min, Byte> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Byte value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.longValue() >= minValue;
        }
    }

    public static class ForDouble implements ConstraintValidator<Min, Double> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Double.isNaN(value)) {
                return false;
            }
            return value >= minValue;
        }
    }

    public static class ForFloat implements ConstraintValidator<Min, Float> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Float value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Float.isNaN(value)) {
                return false;
            }
            return value.doubleValue() >= minValue;
        }
    }

    public static class ForCharSequence implements ConstraintValidator<Min, CharSequence> {

        private long minValue;

        @Override
        public void initialize(Min constraintAnnotation) {
            this.minValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            try {
                return new BigDecimal(value.toString()).compareTo(BigDecimal.valueOf(minValue)) >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
