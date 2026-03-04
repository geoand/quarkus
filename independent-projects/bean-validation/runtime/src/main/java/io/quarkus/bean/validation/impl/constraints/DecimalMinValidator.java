package io.quarkus.bean.validation.impl.constraints;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.DecimalMin;

public class DecimalMinValidator {

    private DecimalMinValidator() {
    }

    public static class ForNumber implements ConstraintValidator<DecimalMin, Number> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
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
            int comparison = bdValue.compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForBigDecimal implements ConstraintValidator<DecimalMin, BigDecimal> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = value.compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForBigInteger implements ConstraintValidator<DecimalMin, BigInteger> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(BigInteger value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = new BigDecimal(value).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForLong implements ConstraintValidator<DecimalMin, Long> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForInteger implements ConstraintValidator<DecimalMin, Integer> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value.longValue()).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForShort implements ConstraintValidator<DecimalMin, Short> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Short value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value.longValue()).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForByte implements ConstraintValidator<DecimalMin, Byte> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(Byte value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            int comparison = BigDecimal.valueOf(value.longValue()).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForDouble implements ConstraintValidator<DecimalMin, Double> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
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
            int comparison = BigDecimal.valueOf(value).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForFloat implements ConstraintValidator<DecimalMin, Float> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
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
            int comparison = BigDecimal.valueOf(value.doubleValue()).compareTo(minValue);
            return inclusive ? comparison >= 0 : comparison > 0;
        }
    }

    public static class ForCharSequence implements ConstraintValidator<DecimalMin, CharSequence> {

        private BigDecimal minValue;
        private boolean inclusive;

        @Override
        public void initialize(DecimalMin constraintAnnotation) {
            this.minValue = new BigDecimal(constraintAnnotation.value());
            this.inclusive = constraintAnnotation.inclusive();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            try {
                BigDecimal bdValue = new BigDecimal(value.toString());
                int comparison = bdValue.compareTo(minValue);
                return inclusive ? comparison >= 0 : comparison > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
