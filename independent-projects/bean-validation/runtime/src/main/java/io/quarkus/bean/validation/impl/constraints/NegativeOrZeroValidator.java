package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.NegativeOrZero;

public class NegativeOrZeroValidator {

    private NegativeOrZeroValidator() {
    }

    public static class ForByte extends AbstractNumberSignValidator.ForByte<NegativeOrZero> {
        public ForByte() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForShort extends AbstractNumberSignValidator.ForShort<NegativeOrZero> {
        public ForShort() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForInteger extends AbstractNumberSignValidator.ForInteger<NegativeOrZero> {
        public ForInteger() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForLong extends AbstractNumberSignValidator.ForLong<NegativeOrZero> {
        public ForLong() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForFloat extends AbstractNumberSignValidator.ForFloat<NegativeOrZero> {
        public ForFloat() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForDouble extends AbstractNumberSignValidator.ForDouble<NegativeOrZero> {
        public ForDouble() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForBigDecimal extends AbstractNumberSignValidator.ForBigDecimal<NegativeOrZero> {
        public ForBigDecimal() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForBigInteger extends AbstractNumberSignValidator.ForBigInteger<NegativeOrZero> {
        public ForBigInteger() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }

    public static class ForNumber extends AbstractNumberSignValidator.ForNumber<NegativeOrZero> {
        public ForNumber() {
            super(SignMode.NEGATIVE_OR_ZERO);
        }
    }
}
