/*
 * Adapted from Hibernate Validator's SizeValidatorTest.
 * Original authors: Alaa Nassef, Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.annotation;
import static io.quarkus.bean.validation.test.constraints.ConstraintAnnotationHelper.attrs;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;

import io.quarkus.bean.validation.impl.constraints.SizeValidator;

class SizeValidatorTest {

    @Test
    void testIsValidObjectArray() {
        ConstraintValidator<Size, Object[]> validator = getValidatorMin1Max2(new SizeValidator.ForArray());
        assertSizes(validator, Object[].class);
    }

    @Test
    void testIsValidBooleanArray() {
        ConstraintValidator<Size, boolean[]> validator = getValidatorMin1Max2(new SizeValidator.ForBooleanArray());
        assertSizes(validator, boolean[].class);
    }

    @Test
    void testIsValidByteArray() {
        ConstraintValidator<Size, byte[]> validator = getValidatorMin1Max2(new SizeValidator.ForByteArray());
        assertSizes(validator, byte[].class);
    }

    @Test
    void testIsValidCharArray() {
        ConstraintValidator<Size, char[]> validator = getValidatorMin1Max2(new SizeValidator.ForCharArray());
        assertSizes(validator, char[].class);
    }

    @Test
    void testIsValidDoubleArray() {
        ConstraintValidator<Size, double[]> validator = getValidatorMin1Max2(new SizeValidator.ForDoubleArray());
        assertSizes(validator, double[].class);
    }

    @Test
    void testIsValidFloatArray() {
        ConstraintValidator<Size, float[]> validator = getValidatorMin1Max2(new SizeValidator.ForFloatArray());
        assertSizes(validator, float[].class);
    }

    @Test
    void testIsValidIntArray() {
        ConstraintValidator<Size, int[]> validator = getValidatorMin1Max2(new SizeValidator.ForIntArray());
        assertSizes(validator, int[].class);
    }

    @Test
    void testIsValidLongArray() {
        ConstraintValidator<Size, long[]> validator = getValidatorMin1Max2(new SizeValidator.ForLongArray());
        assertSizes(validator, long[].class);
    }

    @Test
    void testIsValidShortArray() {
        ConstraintValidator<Size, short[]> validator = getValidatorMin1Max2(new SizeValidator.ForShortArray());
        assertSizes(validator, short[].class);
    }

    @Test
    void testIsValidCollection() {
        ConstraintValidator<Size, Collection<?>> validator = getValidatorMin1Max2(new SizeValidator.ForCollection());

        assertThat(validator.isValid(null, null)).isTrue();

        Collection<String> collection = new ArrayList<>();
        assertThat(validator.isValid(collection, null)).isFalse();

        collection.add("firstItem");
        assertThat(validator.isValid(collection, null)).isTrue();

        collection.add("secondItem");
        assertThat(validator.isValid(collection, null)).isTrue();

        collection.add("thirdItem");
        assertThat(validator.isValid(collection, null)).isFalse();
    }

    @Test
    void testIsValidMap() {
        ConstraintValidator<Size, Map<?, ?>> validator = getValidatorMin1Max2(new SizeValidator.ForMap());

        assertThat(validator.isValid(null, null)).isTrue();

        Map<String, String> map = new HashMap<>();
        assertThat(validator.isValid(map, null)).isFalse();

        map.put("key1", "firstItem");
        assertThat(validator.isValid(map, null)).isTrue();

        map.put("key3", "secondItem");
        assertThat(validator.isValid(map, null)).isTrue();

        map.put("key2", "thirdItem");
        assertThat(validator.isValid(map, null)).isFalse();
    }

    @Test
    void testIsValidString() {
        ConstraintValidator<Size, CharSequence> validator = getValidatorMin1Max2(new SizeValidator.ForCharSequence());

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid("a", null)).isTrue();
        assertThat(validator.isValid("ab", null)).isTrue();
        assertThat(validator.isValid("abc", null)).isFalse();
    }

    @Test
    void testIsValidCharSequence() {
        ConstraintValidator<Size, CharSequence> validator = getValidatorMin1Max2(new SizeValidator.ForCharSequence());

        assertThat(validator.isValid(new StringBuilder("ab"), null)).isTrue();
        assertThat(validator.isValid(new StringBuilder("abc"), null)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private <T> ConstraintValidator<Size, T> getValidatorMin1Max2(ConstraintValidator<Size, T> validator) {
        Size m = annotation(Size.class, attrs("min", 1, "max", 2));
        validator.initialize(m);
        return validator;
    }

    @SuppressWarnings("unchecked")
    private <T> void assertSizes(ConstraintValidator<Size, T> validator, Class<T> arrayType) {
        assertThat(validator.isValid(null, null)).isTrue();

        T array = (T) Array.newInstance(arrayType.getComponentType(), 0);
        assertThat(validator.isValid(array, null)).isFalse();

        array = (T) Array.newInstance(arrayType.getComponentType(), 1);
        assertThat(validator.isValid(array, null)).isTrue();

        array = (T) Array.newInstance(arrayType.getComponentType(), 2);
        assertThat(validator.isValid(array, null)).isTrue();

        array = (T) Array.newInstance(arrayType.getComponentType(), 3);
        assertThat(validator.isValid(array, null)).isFalse();
    }
}
