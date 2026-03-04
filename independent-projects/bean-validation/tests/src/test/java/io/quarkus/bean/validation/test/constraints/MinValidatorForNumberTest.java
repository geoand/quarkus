/*
 * Adapted from Hibernate Validator's MinValidatorForNumberTest and BaseMinMaxValidatorForNumberTest.
 * Original authors: Alaa Nassef, Hardy Ferentschik, Xavier Sosnovsky, Marko Bekhta
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.attrs;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.DecimalMinValidator;
import io.quarkus.bean.validation.impl.constraints.MinValidator;

class MinValidatorForNumberTest {

    @Test
    void testIsValidMinValidator() {
        Min m = annotation(Min.class, "value", 15L);

        testMin(m, true);
    }

    @Test
    void testIsValidDecimalMinValidator() {
        DecimalMin m = annotation(DecimalMin.class, "value", "1500E-2");

        testDecimalMin(m, true);
    }

    @Test
    void testIsValidDecimalMinExclusive() {
        DecimalMin m = annotation(DecimalMin.class, attrs("value", "1500E-2", "inclusive", false));

        testDecimalMin(m, false);
    }

    private void testMin(Min m, boolean inclusive) {
        MinValidator.ForNumber constraint = new MinValidator.ForNumber();
        constraint.initialize(m);
        testNumberValidator(constraint, inclusive, false);

        MinValidator.ForBigDecimal bdConstraint = new MinValidator.ForBigDecimal();
        bdConstraint.initialize(m);
        testValidatorBigDecimal(bdConstraint, inclusive, false);

        MinValidator.ForBigInteger biConstraint = new MinValidator.ForBigInteger();
        biConstraint.initialize(m);
        testValidatorBigInteger(biConstraint, inclusive, false);

        MinValidator.ForByte byteConstraint = new MinValidator.ForByte();
        byteConstraint.initialize(m);
        testValidatorByte(byteConstraint, inclusive, false);

        MinValidator.ForShort shortConstraint = new MinValidator.ForShort();
        shortConstraint.initialize(m);
        testValidatorShort(shortConstraint, inclusive, false);

        MinValidator.ForInteger intConstraint = new MinValidator.ForInteger();
        intConstraint.initialize(m);
        testValidatorInteger(intConstraint, inclusive, false);

        MinValidator.ForLong longConstraint = new MinValidator.ForLong();
        longConstraint.initialize(m);
        testValidatorLong(longConstraint, inclusive, false);

        MinValidator.ForFloat floatConstraint = new MinValidator.ForFloat();
        floatConstraint.initialize(m);
        testValidatorFloat(floatConstraint, inclusive, false);

        MinValidator.ForDouble doubleConstraint = new MinValidator.ForDouble();
        doubleConstraint.initialize(m);
        testValidatorDouble(doubleConstraint, inclusive, false);
    }

    private void testDecimalMin(DecimalMin m, boolean inclusive) {
        DecimalMinValidator.ForNumber constraint = new DecimalMinValidator.ForNumber();
        constraint.initialize(m);
        testNumberValidator(constraint, inclusive, false);

        DecimalMinValidator.ForBigDecimal bdConstraint = new DecimalMinValidator.ForBigDecimal();
        bdConstraint.initialize(m);
        testValidatorBigDecimal(bdConstraint, inclusive, false);

        DecimalMinValidator.ForBigInteger biConstraint = new DecimalMinValidator.ForBigInteger();
        biConstraint.initialize(m);
        testValidatorBigInteger(biConstraint, inclusive, false);

        DecimalMinValidator.ForByte byteConstraint = new DecimalMinValidator.ForByte();
        byteConstraint.initialize(m);
        testValidatorByte(byteConstraint, inclusive, false);

        DecimalMinValidator.ForShort shortConstraint = new DecimalMinValidator.ForShort();
        shortConstraint.initialize(m);
        testValidatorShort(shortConstraint, inclusive, false);

        DecimalMinValidator.ForInteger intConstraint = new DecimalMinValidator.ForInteger();
        intConstraint.initialize(m);
        testValidatorInteger(intConstraint, inclusive, false);

        DecimalMinValidator.ForLong longConstraint = new DecimalMinValidator.ForLong();
        longConstraint.initialize(m);
        testValidatorLong(longConstraint, inclusive, false);

        DecimalMinValidator.ForFloat floatConstraint = new DecimalMinValidator.ForFloat();
        floatConstraint.initialize(m);
        testValidatorFloatFiniteOnly(floatConstraint, inclusive, false);

        DecimalMinValidator.ForDouble doubleConstraint = new DecimalMinValidator.ForDouble();
        doubleConstraint.initialize(m);
        testValidatorDoubleFiniteOnly(doubleConstraint, inclusive, false);
    }

    // --- Adapted from BaseMinMaxValidatorForNumberTest ---

    private void testNumberValidator(ConstraintValidator<?, Number> constraint, boolean inclusive, boolean isMax) {
        byte b = 1;
        Byte bWrapper = 127;
        if (inclusive) {
            assertThat(constraint.isValid(15L, null)).isTrue();
            assertThat(constraint.isValid(15, null)).isTrue();
            assertThat(constraint.isValid(15.0, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15L, null)).isFalse();
            assertThat(constraint.isValid(15, null)).isFalse();
            assertThat(constraint.isValid(15.0, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(10, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(b, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(14.99, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(-14.99, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(14.99F, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(-14.99F, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(14, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(16, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid((short) 14, null)).isEqualTo(isMax);
        assertThat(constraint.isValid((short) 16, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(BigInteger.valueOf(14L), null)).isEqualTo(isMax);
        assertThat(constraint.isValid(BigInteger.valueOf(16L), null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(BigDecimal.valueOf(14L), null)).isEqualTo(isMax);
        assertThat(constraint.isValid(BigDecimal.valueOf(16L), null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(new BigDecimal("14.99"), null)).isEqualTo(isMax);
        assertThat(constraint.isValid(new BigDecimal("15.001"), null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(bWrapper, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(20, null)).isEqualTo(!isMax);
    }

    private void testValidatorBigDecimal(ConstraintValidator<?, BigDecimal> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(BigDecimal.valueOf(15L), null)).isTrue();
            assertThat(constraint.isValid(BigDecimal.valueOf(15), null)).isTrue();
            assertThat(constraint.isValid(BigDecimal.valueOf(15.0), null)).isTrue();
        } else {
            assertThat(constraint.isValid(BigDecimal.valueOf(15L), null)).isFalse();
            assertThat(constraint.isValid(BigDecimal.valueOf(15), null)).isFalse();
            assertThat(constraint.isValid(BigDecimal.valueOf(15.0), null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(BigDecimal.valueOf(-156000000000.0), null)).isEqualTo(isMax);
        assertThat(constraint.isValid(BigDecimal.valueOf(156000000000.0), null)).isEqualTo(!isMax);
    }

    private void testValidatorBigInteger(ConstraintValidator<?, BigInteger> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(BigInteger.valueOf(15L), null)).isTrue();
            assertThat(constraint.isValid(BigInteger.valueOf(15), null)).isTrue();
        } else {
            assertThat(constraint.isValid(BigInteger.valueOf(15L), null)).isFalse();
            assertThat(constraint.isValid(BigInteger.valueOf(15), null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(BigInteger.valueOf(-1560000000), null)).isEqualTo(isMax);
        assertThat(constraint.isValid(BigInteger.valueOf(1560000000), null)).isEqualTo(!isMax);
    }

    private void testValidatorByte(ConstraintValidator<?, Byte> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid((byte) 15, null)).isTrue();
        } else {
            assertThat(constraint.isValid((byte) 15, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid((byte) 14, null)).isEqualTo(isMax);
        assertThat(constraint.isValid((byte) 16, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Byte.MIN_VALUE, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(Byte.MAX_VALUE, null)).isEqualTo(!isMax);
    }

    private void testValidatorShort(ConstraintValidator<?, Short> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid((short) 15, null)).isTrue();
        } else {
            assertThat(constraint.isValid((short) 15, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid((short) 14, null)).isEqualTo(isMax);
        assertThat(constraint.isValid((short) 16, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Short.MIN_VALUE, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(Short.MAX_VALUE, null)).isEqualTo(!isMax);
    }

    private void testValidatorInteger(ConstraintValidator<?, Integer> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(15, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(14, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(16, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Integer.MIN_VALUE, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(Integer.MAX_VALUE, null)).isEqualTo(!isMax);
    }

    private void testValidatorLong(ConstraintValidator<?, Long> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(15L, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15L, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(-1560000000L, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(1560000000L, null)).isEqualTo(!isMax);
    }

    private void testValidatorDouble(ConstraintValidator<?, Double> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(15D, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15D, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(14.99, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(15.001, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(-14.99, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(-1560000000D, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(Double.NEGATIVE_INFINITY, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(1560000000D, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Double.NaN, null)).isFalse();
        assertThat(constraint.isValid(Double.POSITIVE_INFINITY, null)).isEqualTo(!isMax);
    }

    private void testValidatorFloat(ConstraintValidator<?, Float> constraint, boolean inclusive, boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(15F, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15F, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(-1560000000F, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(Float.NEGATIVE_INFINITY, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(1560000000F, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Float.NaN, null)).isFalse();
        assertThat(constraint.isValid(Float.POSITIVE_INFINITY, null)).isEqualTo(!isMax);
    }

    private void testValidatorFloatFiniteOnly(ConstraintValidator<?, Float> constraint, boolean inclusive,
            boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(15F, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15F, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(-1560000000F, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(1560000000F, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Float.NaN, null)).isFalse();
    }

    private void testValidatorDoubleFiniteOnly(ConstraintValidator<?, Double> constraint, boolean inclusive,
            boolean isMax) {
        if (inclusive) {
            assertThat(constraint.isValid(15D, null)).isTrue();
        } else {
            assertThat(constraint.isValid(15D, null)).isFalse();
        }

        assertThat(constraint.isValid(null, null)).isTrue();
        assertThat(constraint.isValid(14.99, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(15.001, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(-14.99, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(-1560000000D, null)).isEqualTo(isMax);
        assertThat(constraint.isValid(1560000000D, null)).isEqualTo(!isMax);
        assertThat(constraint.isValid(Double.NaN, null)).isFalse();
    }
}
