package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.PositiveOrZero;

public class PositiveOrZeroValidator {

    private PositiveOrZeroValidator() {
    }

    public static class ForByte extends AbstractNumberSignValidator.ForByte<PositiveOrZero> {
        public ForByte() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForShort extends AbstractNumberSignValidator.ForShort<PositiveOrZero> {
        public ForShort() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForInteger extends AbstractNumberSignValidator.ForInteger<PositiveOrZero> {
        public ForInteger() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForLong extends AbstractNumberSignValidator.ForLong<PositiveOrZero> {
        public ForLong() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForFloat extends AbstractNumberSignValidator.ForFloat<PositiveOrZero> {
        public ForFloat() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForDouble extends AbstractNumberSignValidator.ForDouble<PositiveOrZero> {
        public ForDouble() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForBigDecimal extends AbstractNumberSignValidator.ForBigDecimal<PositiveOrZero> {
        public ForBigDecimal() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForBigInteger extends AbstractNumberSignValidator.ForBigInteger<PositiveOrZero> {
        public ForBigInteger() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }

    public static class ForNumber extends AbstractNumberSignValidator.ForNumber<PositiveOrZero> {
        public ForNumber() {
            super(SignMode.POSITIVE_OR_ZERO);
        }
    }
}
