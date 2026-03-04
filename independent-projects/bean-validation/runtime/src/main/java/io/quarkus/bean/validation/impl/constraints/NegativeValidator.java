package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.Negative;

public class NegativeValidator {

    private NegativeValidator() {
    }

    public static class ForByte extends AbstractNumberSignValidator.ForByte<Negative> {
        public ForByte() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForShort extends AbstractNumberSignValidator.ForShort<Negative> {
        public ForShort() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForInteger extends AbstractNumberSignValidator.ForInteger<Negative> {
        public ForInteger() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForLong extends AbstractNumberSignValidator.ForLong<Negative> {
        public ForLong() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForFloat extends AbstractNumberSignValidator.ForFloat<Negative> {
        public ForFloat() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForDouble extends AbstractNumberSignValidator.ForDouble<Negative> {
        public ForDouble() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForBigDecimal extends AbstractNumberSignValidator.ForBigDecimal<Negative> {
        public ForBigDecimal() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForBigInteger extends AbstractNumberSignValidator.ForBigInteger<Negative> {
        public ForBigInteger() {
            super(SignMode.NEGATIVE);
        }
    }

    public static class ForNumber extends AbstractNumberSignValidator.ForNumber<Negative> {
        public ForNumber() {
            super(SignMode.NEGATIVE);
        }
    }
}
