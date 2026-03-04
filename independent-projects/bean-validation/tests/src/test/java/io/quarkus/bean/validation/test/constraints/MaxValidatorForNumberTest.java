/*
 * Adapted from Hibernate Validator's MaxValidatorForNumberTest and BaseMinMaxValidatorForNumberTest.
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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.DecimalMaxValidator;
import io.quarkus.bean.validation.impl.constraints.MaxValidator;

class MaxValidatorForNumberTest {

    @Test
    void testIsValidMax() {
        Max m = annotation(Max.class, "value", 15L);

        testMax(m, true);
    }

    @Test
    void testIsValidDecimalMax() {
        DecimalMax m = annotation(DecimalMax.class, "value", "15.0E0");

        testDecimalMax(m, true);
    }

    @Test
    void testIsValidDecimalMaxExclusive() {
        DecimalMax m = annotation(DecimalMax.class, attrs("value", "15.0E0", "inclusive", false));

        testDecimalMax(m, false);
    }

    @Test
    void testIsValidNumberForFloatingPointStoredAsNumber() {
        Max m = annotation(Max.class, "value", 1L);
        MaxValidator.ForNumber validator = new MaxValidator.ForNumber();
        validator.initialize(m);

        assertThat(validator.isValid(1.01, null)).isFalse();
        assertThat(validator.isValid(1.01F, null)).isFalse();
        assertThat(validator.isValid(new BigDecimal("1.01"), null)).isFalse();
        assertThat(validator.isValid(new BigInteger("2"), null)).isFalse();
        assertThat(validator.isValid(Double.POSITIVE_INFINITY, null)).isFalse();
        assertThat(validator.isValid(Float.POSITIVE_INFINITY, null)).isFalse();
    }

    private void testMax(Max m, boolean inclusive) {
        MaxValidator.ForNumber constraint = new MaxValidator.ForNumber();
        constraint.initialize(m);
        testNumberValidator(constraint, inclusive, true);

        MaxValidator.ForBigDecimal bdConstraint = new MaxValidator.ForBigDecimal();
        bdConstraint.initialize(m);
        testValidatorBigDecimal(bdConstraint, inclusive, true);

        MaxValidator.ForBigInteger biConstraint = new MaxValidator.ForBigInteger();
        biConstraint.initialize(m);
        testValidatorBigInteger(biConstraint, inclusive, true);

        MaxValidator.ForByte byteConstraint = new MaxValidator.ForByte();
        byteConstraint.initialize(m);
        testValidatorByte(byteConstraint, inclusive, true);

        MaxValidator.ForShort shortConstraint = new MaxValidator.ForShort();
        shortConstraint.initialize(m);
        testValidatorShort(shortConstraint, inclusive, true);

        MaxValidator.ForInteger intConstraint = new MaxValidator.ForInteger();
        intConstraint.initialize(m);
        testValidatorInteger(intConstraint, inclusive, true);

        MaxValidator.ForLong longConstraint = new MaxValidator.ForLong();
        longConstraint.initialize(m);
        testValidatorLong(longConstraint, inclusive, true);

        MaxValidator.ForFloat floatConstraint = new MaxValidator.ForFloat();
        floatConstraint.initialize(m);
        testValidatorFloat(floatConstraint, inclusive, true);

        MaxValidator.ForDouble doubleConstraint = new MaxValidator.ForDouble();
        doubleConstraint.initialize(m);
        testValidatorDouble(doubleConstraint, inclusive, true);
    }

    private void testDecimalMax(DecimalMax m, boolean inclusive) {
        DecimalMaxValidator.ForNumber constraint = new DecimalMaxValidator.ForNumber();
        constraint.initialize(m);
        testNumberValidator(constraint, inclusive, true);

        DecimalMaxValidator.ForBigDecimal bdConstraint = new DecimalMaxValidator.ForBigDecimal();
        bdConstraint.initialize(m);
        testValidatorBigDecimal(bdConstraint, inclusive, true);

        DecimalMaxValidator.ForBigInteger biConstraint = new DecimalMaxValidator.ForBigInteger();
        biConstraint.initialize(m);
        testValidatorBigInteger(biConstraint, inclusive, true);

        DecimalMaxValidator.ForByte byteConstraint = new DecimalMaxValidator.ForByte();
        byteConstraint.initialize(m);
        testValidatorByte(byteConstraint, inclusive, true);

        DecimalMaxValidator.ForShort shortConstraint = new DecimalMaxValidator.ForShort();
        shortConstraint.initialize(m);
        testValidatorShort(shortConstraint, inclusive, true);

        DecimalMaxValidator.ForInteger intConstraint = new DecimalMaxValidator.ForInteger();
        intConstraint.initialize(m);
        testValidatorInteger(intConstraint, inclusive, true);

        DecimalMaxValidator.ForLong longConstraint = new DecimalMaxValidator.ForLong();
        longConstraint.initialize(m);
        testValidatorLong(longConstraint, inclusive, true);

        DecimalMaxValidator.ForFloat floatConstraint = new DecimalMaxValidator.ForFloat();
        floatConstraint.initialize(m);
        testValidatorFloatFiniteOnly(floatConstraint, inclusive, true);

        DecimalMaxValidator.ForDouble doubleConstraint = new DecimalMaxValidator.ForDouble();
        doubleConstraint.initialize(m);
        testValidatorDoubleFiniteOnly(doubleConstraint, inclusive, true);
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

    private void testValidatorBigDecimal(ConstraintValidator<?, BigDecimal> constraint, boolean inclusive,
            boolean isMax) {
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

    private void testValidatorBigInteger(ConstraintValidator<?, BigInteger> constraint, boolean inclusive,
            boolean isMax) {
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
