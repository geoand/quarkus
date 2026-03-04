package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.Positive;

public class PositiveValidator {

    private PositiveValidator() {
    }

    public static class ForByte extends AbstractNumberSignValidator.ForByte<Positive> {
        public ForByte() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForShort extends AbstractNumberSignValidator.ForShort<Positive> {
        public ForShort() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForInteger extends AbstractNumberSignValidator.ForInteger<Positive> {
        public ForInteger() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForLong extends AbstractNumberSignValidator.ForLong<Positive> {
        public ForLong() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForFloat extends AbstractNumberSignValidator.ForFloat<Positive> {
        public ForFloat() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForDouble extends AbstractNumberSignValidator.ForDouble<Positive> {
        public ForDouble() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForBigDecimal extends AbstractNumberSignValidator.ForBigDecimal<Positive> {
        public ForBigDecimal() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForBigInteger extends AbstractNumberSignValidator.ForBigInteger<Positive> {
        public ForBigInteger() {
            super(SignMode.POSITIVE);
        }
    }

    public static class ForNumber extends AbstractNumberSignValidator.ForNumber<Positive> {
        public ForNumber() {
            super(SignMode.POSITIVE);
        }
    }
}
