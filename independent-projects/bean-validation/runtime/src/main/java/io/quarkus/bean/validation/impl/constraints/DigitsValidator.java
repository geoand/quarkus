package io.quarkus.bean.validation.impl.constraints;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Digits;

public class DigitsValidator {

    private DigitsValidator() {
    }

    private static boolean isValid(BigDecimal value, int maxIntegerLength, int maxFractionLength) {
        BigDecimal stripped = value.stripTrailingZeros();
        int integerPartLength = stripped.precision() - stripped.scale();
        int fractionPartLength = Math.max(stripped.scale(), 0);
        return integerPartLength <= maxIntegerLength && fractionPartLength <= maxFractionLength;
    }

    public static class ForNumber implements ConstraintValidator<Digits, Number> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
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
            return DigitsValidator.isValid(bdValue, maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForBigDecimal implements ConstraintValidator<Digits, BigDecimal> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(value, maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForBigInteger implements ConstraintValidator<Digits, BigInteger> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(BigInteger value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(new BigDecimal(value), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForLong implements ConstraintValidator<Digits, Long> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(BigDecimal.valueOf(value), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForInteger implements ConstraintValidator<Digits, Integer> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(BigDecimal.valueOf(value.longValue()), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForShort implements ConstraintValidator<Digits, Short> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Short value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(BigDecimal.valueOf(value.longValue()), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForByte implements ConstraintValidator<Digits, Byte> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Byte value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(BigDecimal.valueOf(value.longValue()), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForDouble implements ConstraintValidator<Digits, Double> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(BigDecimal.valueOf(value), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForFloat implements ConstraintValidator<Digits, Float> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(Float value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return DigitsValidator.isValid(BigDecimal.valueOf(value.doubleValue()), maxIntegerLength, maxFractionLength);
        }
    }

    public static class ForCharSequence implements ConstraintValidator<Digits, CharSequence> {

        private int maxIntegerLength;
        private int maxFractionLength;

        @Override
        public void initialize(Digits constraintAnnotation) {
            this.maxIntegerLength = constraintAnnotation.integer();
            this.maxFractionLength = constraintAnnotation.fraction();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            try {
                BigDecimal bdValue = new BigDecimal(value.toString());
                return DigitsValidator.isValid(bdValue, maxIntegerLength, maxFractionLength);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
