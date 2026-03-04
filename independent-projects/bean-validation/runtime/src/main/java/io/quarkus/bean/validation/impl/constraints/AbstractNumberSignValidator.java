package io.quarkus.bean.validation.impl.constraints;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Base class for number sign validators (Positive, PositiveOrZero, Negative, NegativeOrZero).
 *
 * @param <A> the constraint annotation type
 * @param <T> the validated number type
 */
abstract class AbstractNumberSignValidator<A extends Annotation, T> implements ConstraintValidator<A, T> {

    /**
     * Defines the comparison mode for the sign check.
     */
    enum SignMode {
        /** Value must be strictly positive (> 0) */
        POSITIVE,
        /** Value must be positive or zero (>= 0) */
        POSITIVE_OR_ZERO,
        /** Value must be strictly negative (< 0) */
        NEGATIVE,
        /** Value must be negative or zero (<= 0) */
        NEGATIVE_OR_ZERO
    }

    private final SignMode mode;

    protected AbstractNumberSignValidator(SignMode mode) {
        this.mode = mode;
    }

    @Override
    public void initialize(A constraintAnnotation) {
    }

    /**
     * Tests whether the given signum satisfies the sign mode.
     *
     * @param signum -1, 0, or 1 as the value is negative, zero, or positive
     * @return true if valid
     */
    protected boolean isValidSignum(int signum) {
        switch (mode) {
            case POSITIVE:
                return signum > 0;
            case POSITIVE_OR_ZERO:
                return signum >= 0;
            case NEGATIVE:
                return signum < 0;
            case NEGATIVE_OR_ZERO:
                return signum <= 0;
            default:
                return false;
        }
    }

    // ---- Concrete inner base classes for each Number type ----

    static abstract class ForByte<A extends Annotation> extends AbstractNumberSignValidator<A, Byte> {
        protected ForByte(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Byte value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return isValidSignum(Byte.compare(value, (byte) 0));
        }
    }

    static abstract class ForShort<A extends Annotation> extends AbstractNumberSignValidator<A, Short> {
        protected ForShort(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Short value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return isValidSignum(Short.compare(value, (short) 0));
        }
    }

    static abstract class ForInteger<A extends Annotation> extends AbstractNumberSignValidator<A, Integer> {
        protected ForInteger(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return isValidSignum(Integer.compare(value, 0));
        }
    }

    static abstract class ForLong<A extends Annotation> extends AbstractNumberSignValidator<A, Long> {
        protected ForLong(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return isValidSignum(Long.compare(value, 0L));
        }
    }

    static abstract class ForFloat<A extends Annotation> extends AbstractNumberSignValidator<A, Float> {
        protected ForFloat(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Float value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Float.isNaN(value)) {
                return false;
            }
            return isValidSignum(Float.compare(value, 0f));
        }
    }

    static abstract class ForDouble<A extends Annotation> extends AbstractNumberSignValidator<A, Double> {
        protected ForDouble(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (Double.isNaN(value)) {
                return false;
            }
            return isValidSignum(Double.compare(value, 0d));
        }
    }

    static abstract class ForBigDecimal<A extends Annotation> extends AbstractNumberSignValidator<A, BigDecimal> {
        protected ForBigDecimal(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return isValidSignum(value.signum());
        }
    }

    static abstract class ForBigInteger<A extends Annotation> extends AbstractNumberSignValidator<A, BigInteger> {
        protected ForBigInteger(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(BigInteger value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return isValidSignum(value.signum());
        }
    }

    static abstract class ForNumber<A extends Annotation> extends AbstractNumberSignValidator<A, Number> {
        protected ForNumber(SignMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (value instanceof BigDecimal bd) {
                return isValidSignum(bd.signum());
            }
            if (value instanceof BigInteger bi) {
                return isValidSignum(bi.signum());
            }
            if (value instanceof Double || value instanceof Float) {
                double d = value.doubleValue();
                if (Double.isNaN(d)) {
                    return false;
                }
                return isValidSignum(Double.compare(d, 0d));
            }
            return isValidSignum(Long.compare(value.longValue(), 0L));
        }
    }
}
