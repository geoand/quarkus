package io.quarkus.bean.validation.impl.constraints;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Max;

public class MaxValidator {

    private MaxValidator() {
    }

    public static class ForNumber implements ConstraintValidator<Max, Number> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (value instanceof BigDecimal bd) {
                return bd.compareTo(BigDecimal.valueOf(maxValue)) <= 0;
            }
            if (value instanceof BigInteger bi) {
                return bi.compareTo(BigInteger.valueOf(maxValue)) <= 0;
            }
            if (value instanceof Double || value instanceof Float) {
                double d = value.doubleValue();
                if (Double.isNaN(d)) {
                    return false;
                }
                return d <= maxValue;
            }
            return value.longValue() <= maxValue;
        }
    }

    public static class ForBigDecimal implements ConstraintValidator<Max, BigDecimal> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.compareTo(BigDecimal.valueOf(maxValue)) <= 0;
        }
    }

    public static class ForBigInteger implements ConstraintValidator<Max, BigInteger> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(BigInteger value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.compareTo(BigInteger.valueOf(maxValue)) <= 0;
        }
    }

    public static class ForLong implements ConstraintValidator<Max, Long> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value <= maxValue;
        }
    }

    public static class ForInteger implements ConstraintValidator<Max, Integer> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.longValue() <= maxValue;
        }
    }

    public static class ForShort implements ConstraintValidator<Max, Short> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Short value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.longValue() <= maxValue;
        }
    }

    public static class ForByte implements ConstraintValidator<Max, Byte> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Byte value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.longValue() <= maxValue;
        }
    }

    public static class ForDouble implements ConstraintValidator<Max, Double> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Double.isNaN(value)) {
                return false;
            }
            return value <= maxValue;
        }
    }

    public static class ForFloat implements ConstraintValidator<Max, Float> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(Float value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Float.isNaN(value)) {
                return false;
            }
            return value.doubleValue() <= maxValue;
        }
    }

    public static class ForCharSequence implements ConstraintValidator<Max, CharSequence> {

        private long maxValue;

        @Override
        public void initialize(Max constraintAnnotation) {
            this.maxValue = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            try {
                return new BigDecimal(value.toString()).compareTo(BigDecimal.valueOf(maxValue)) <= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
