package io.quarkus.bean.validation.impl.constraints;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.DecimalMax;

public class DecimalMaxValidator {

    private DecimalMaxValidator() {
    }

    public static class ForNumber implements ConstraintValidator<DecimalMax, Number> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if ((value instanceof Double || value instanceof Float) && Double.isNaN(value.doubleValue())) {
                return false;
            }
            BigDecimal bdValue;
            if (value instanceof BigDecimal bd) {
                bdValue = bd;
            } else if (value instanceof BigInteger bi) {
                bdValue = new BigDecimal(bi);
            } else if (value instanceof Double || value instanceof Float) {
                bdValue = BigDecimal.valueOf(value.doubleValue());
            } else {
                bdValue = BigDecimal.valueOf(value.longValue());
            }
            int comparison = bdValue.compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForBigDecimal implements ConstraintValidator<DecimalMax, BigDecimal> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = value.compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForBigInteger implements ConstraintValidator<DecimalMax, BigInteger> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(BigInteger value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = new BigDecimal(value).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForLong implements ConstraintValidator<DecimalMax, Long> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForInteger implements ConstraintValidator<DecimalMax, Integer> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value.longValue()).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForShort implements ConstraintValidator<DecimalMax, Short> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Short value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value.longValue()).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForByte implements ConstraintValidator<DecimalMax, Byte> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Byte value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value.longValue()).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForDouble implements ConstraintValidator<DecimalMax, Double> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Double.isNaN(value)) {
                return false;
            }
            int comparison = BigDecimal.valueOf(value).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForFloat implements ConstraintValidator<DecimalMax, Float> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Float value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Float.isNaN(value)) {
                return false;
            }
            int comparison = BigDecimal.valueOf(value.doubleValue()).compareTo(maxValue);
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    public static class ForCharSequence implements ConstraintValidator<DecimalMax, CharSequence> {

        private BigDecimal maxValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMax constraintAnnotation) {
            this.maxValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            try {
                BigDecimal bdValue = new BigDecimal(value.toString());
                int comparison = bdValue.compareTo(maxValue);
                return inclusive ? comparison <= 0 : comparison < 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
