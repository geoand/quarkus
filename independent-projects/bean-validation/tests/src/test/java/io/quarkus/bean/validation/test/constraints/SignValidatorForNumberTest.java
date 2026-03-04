/*
 * Adapted from Hibernate Validator's SignValidatorForNumberTest.
 * Original authors: Marko Bekhta, Guillaume Smet
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.NegativeOrZeroValidator;
import io.quarkus.bean.validation.impl.constraints.NegativeValidator;
import io.quarkus.bean.validation.impl.constraints.PositiveOrZeroValidator;
import io.quarkus.bean.validation.impl.constraints.PositiveValidator;

@SuppressWarnings({ "rawtypes", "unchecked" })
class SignValidatorForNumberTest {

    @Test
    void testPositiveValidator() {
        Positive m = annotation(Positive.class);
        testPositive(m);
    }

    @Test
    void testPositiveOrZeroValidator() {
        PositiveOrZero m = annotation(PositiveOrZero.class);
        testPositiveOrZero(m);
    }

    @Test
    void testNegativeValidator() {
        Negative m = annotation(Negative.class);
        testNegative(m);
    }

    @Test
    void testNegativeOrZeroValidator() {
        NegativeOrZero m = annotation(NegativeOrZero.class);
        testNegativeOrZero(m);
    }

    private void testPositive(Positive m) {
        ConstraintValidator validator = new PositiveValidator.ForNumber();
        validator.initialize(m);
        testSignNumber(validator, true, true);

        validator = new PositiveValidator.ForBigDecimal();
        validator.initialize(m);
        testSignBigDecimal(validator, true, true);

        validator = new PositiveValidator.ForBigInteger();
        validator.initialize(m);
        testSignBigInteger(validator, true, true);

        validator = new PositiveValidator.ForLong();
        validator.initialize(m);
        testSignLong(validator, true, true);

        validator = new PositiveValidator.ForFloat();
        validator.initialize(m);
        testSignFloat(validator, true, true);

        validator = new PositiveValidator.ForDouble();
        validator.initialize(m);
        testSignDouble(validator, true, true);

        validator = new PositiveValidator.ForShort();
        validator.initialize(m);
        testSignShort(validator, true, true);

        validator = new PositiveValidator.ForByte();
        validator.initialize(m);
        testSignByte(validator, true, true);

        validator = new PositiveValidator.ForInteger();
        validator.initialize(m);
        testSignInteger(validator, true, true);
    }

    private void testPositiveOrZero(PositiveOrZero m) {
        ConstraintValidator validator = new PositiveOrZeroValidator.ForNumber();
        validator.initialize(m);
        testSignNumber(validator, false, true);

        validator = new PositiveOrZeroValidator.ForBigDecimal();
        validator.initialize(m);
        testSignBigDecimal(validator, false, true);

        validator = new PositiveOrZeroValidator.ForBigInteger();
        validator.initialize(m);
        testSignBigInteger(validator, false, true);

        validator = new PositiveOrZeroValidator.ForLong();
        validator.initialize(m);
        testSignLong(validator, false, true);

        validator = new PositiveOrZeroValidator.ForFloat();
        validator.initialize(m);
        testSignFloat(validator, false, true);

        validator = new PositiveOrZeroValidator.ForDouble();
        validator.initialize(m);
        testSignDouble(validator, false, true);

        validator = new PositiveOrZeroValidator.ForShort();
        validator.initialize(m);
        testSignShort(validator, false, true);

        validator = new PositiveOrZeroValidator.ForByte();
        validator.initialize(m);
        testSignByte(validator, false, true);

        validator = new PositiveOrZeroValidator.ForInteger();
        validator.initialize(m);
        testSignInteger(validator, false, true);
    }

    private void testNegative(Negative m) {
        ConstraintValidator validator = new NegativeValidator.ForNumber();
        validator.initialize(m);
        testSignNumber(validator, true, false);

        validator = new NegativeValidator.ForBigDecimal();
        validator.initialize(m);
        testSignBigDecimal(validator, true, false);

        validator = new NegativeValidator.ForBigInteger();
        validator.initialize(m);
        testSignBigInteger(validator, true, false);

        validator = new NegativeValidator.ForLong();
        validator.initialize(m);
        testSignLong(validator, true, false);

        validator = new NegativeValidator.ForFloat();
        validator.initialize(m);
        testSignFloat(validator, true, false);

        validator = new NegativeValidator.ForDouble();
        validator.initialize(m);
        testSignDouble(validator, true, false);

        validator = new NegativeValidator.ForShort();
        validator.initialize(m);
        testSignShort(validator, true, false);

        validator = new NegativeValidator.ForByte();
        validator.initialize(m);
        testSignByte(validator, true, false);

        validator = new NegativeValidator.ForInteger();
        validator.initialize(m);
        testSignInteger(validator, true, false);
    }

    private void testNegativeOrZero(NegativeOrZero m) {
        ConstraintValidator validator = new NegativeOrZeroValidator.ForNumber();
        validator.initialize(m);
        testSignNumber(validator, false, false);

        validator = new NegativeOrZeroValidator.ForBigDecimal();
        validator.initialize(m);
        testSignBigDecimal(validator, false, false);

        validator = new NegativeOrZeroValidator.ForBigInteger();
        validator.initialize(m);
        testSignBigInteger(validator, false, false);

        validator = new NegativeOrZeroValidator.ForLong();
        validator.initialize(m);
        testSignLong(validator, false, false);

        validator = new NegativeOrZeroValidator.ForFloat();
        validator.initialize(m);
        testSignFloat(validator, false, false);

        validator = new NegativeOrZeroValidator.ForDouble();
        validator.initialize(m);
        testSignDouble(validator, false, false);

        validator = new NegativeOrZeroValidator.ForShort();
        validator.initialize(m);
        testSignShort(validator, false, false);

        validator = new NegativeOrZeroValidator.ForByte();
        validator.initialize(m);
        testSignByte(validator, false, false);

        validator = new NegativeOrZeroValidator.ForInteger();
        validator.initialize(m);
        testSignInteger(validator, false, false);
    }

    private void testSignNumber(ConstraintValidator<?, Number> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(0, null)).isEqualTo(!strict);
        assertThat(validator.isValid(1, null)).isEqualTo(positive);
        assertThat(validator.isValid(-1, null)).isEqualTo(!positive);
        assertThat(validator.isValid(10.0, null)).isEqualTo(positive);
        assertThat(validator.isValid(-10.0, null)).isEqualTo(!positive);
    }

    private void testSignBigDecimal(ConstraintValidator<?, BigDecimal> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(BigDecimal.ZERO, null)).isEqualTo(!strict);
        assertThat(validator.isValid(BigDecimal.ONE, null)).isEqualTo(positive);
        assertThat(validator.isValid(BigDecimal.ONE.negate(), null)).isEqualTo(!positive);
        assertThat(validator.isValid(BigDecimal.TEN, null)).isEqualTo(positive);
        assertThat(validator.isValid(BigDecimal.TEN.negate(), null)).isEqualTo(!positive);
    }

    private void testSignBigInteger(ConstraintValidator<?, BigInteger> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(BigInteger.ZERO, null)).isEqualTo(!strict);
        assertThat(validator.isValid(BigInteger.ONE, null)).isEqualTo(positive);
        assertThat(validator.isValid(BigInteger.ONE.negate(), null)).isEqualTo(!positive);
        assertThat(validator.isValid(BigInteger.TEN, null)).isEqualTo(positive);
        assertThat(validator.isValid(BigInteger.TEN.negate(), null)).isEqualTo(!positive);
    }

    private void testSignLong(ConstraintValidator<?, Long> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(0L, null)).isEqualTo(!strict);
        assertThat(validator.isValid(1L, null)).isEqualTo(positive);
        assertThat(validator.isValid(-1L, null)).isEqualTo(!positive);
        assertThat(validator.isValid(10L, null)).isEqualTo(positive);
        assertThat(validator.isValid(-10L, null)).isEqualTo(!positive);
    }

    private void testSignShort(ConstraintValidator<?, Number> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid((short) 0, null)).isEqualTo(!strict);
        assertThat(validator.isValid((short) 1, null)).isEqualTo(positive);
        assertThat(validator.isValid((short) -1, null)).isEqualTo(!positive);
        assertThat(validator.isValid((short) 10, null)).isEqualTo(positive);
        assertThat(validator.isValid((short) -10, null)).isEqualTo(!positive);
    }

    private void testSignByte(ConstraintValidator<?, Number> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid((byte) 0, null)).isEqualTo(!strict);
        assertThat(validator.isValid((byte) 1, null)).isEqualTo(positive);
        assertThat(validator.isValid((byte) -1, null)).isEqualTo(!positive);
        assertThat(validator.isValid((byte) 10, null)).isEqualTo(positive);
        assertThat(validator.isValid((byte) -10, null)).isEqualTo(!positive);
    }

    private void testSignInteger(ConstraintValidator<?, Number> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(0, null)).isEqualTo(!strict);
        assertThat(validator.isValid(1, null)).isEqualTo(positive);
        assertThat(validator.isValid(-1, null)).isEqualTo(!positive);
        assertThat(validator.isValid(10, null)).isEqualTo(positive);
        assertThat(validator.isValid(-10, null)).isEqualTo(!positive);
    }

    private void testSignDouble(ConstraintValidator<?, Double> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(0D, null)).isEqualTo(!strict);
        assertThat(validator.isValid(1D, null)).isEqualTo(positive);
        assertThat(validator.isValid(-1D, null)).isEqualTo(!positive);
        assertThat(validator.isValid(10D, null)).isEqualTo(positive);
        assertThat(validator.isValid(-10D, null)).isEqualTo(!positive);
        assertThat(validator.isValid(Double.POSITIVE_INFINITY, null)).isEqualTo(positive);
        assertThat(validator.isValid(Double.NEGATIVE_INFINITY, null)).isEqualTo(!positive);
        assertThat(validator.isValid(Double.NaN, null)).isFalse();
    }

    private void testSignFloat(ConstraintValidator<?, Float> validator, boolean strict, boolean positive) {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(0F, null)).isEqualTo(!strict);
        assertThat(validator.isValid(1F, null)).isEqualTo(positive);
        assertThat(validator.isValid(-1F, null)).isEqualTo(!positive);
        assertThat(validator.isValid(10F, null)).isEqualTo(positive);
        assertThat(validator.isValid(-10F, null)).isEqualTo(!positive);
        assertThat(validator.isValid(Float.POSITIVE_INFINITY, null)).isEqualTo(positive);
        assertThat(validator.isValid(Float.NEGATIVE_INFINITY, null)).isEqualTo(!positive);
        assertThat(validator.isValid(Float.NaN, null)).isFalse();
    }
}
