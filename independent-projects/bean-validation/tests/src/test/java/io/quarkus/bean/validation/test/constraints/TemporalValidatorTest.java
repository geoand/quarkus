/*
 * Adapted from Hibernate Validator's TimeValidatorTest and DateHolder.
 * Original authors: Hardy Ferentschik
 * SPDX-License-Identifier: Apache-2.0
 */
package io.quarkus.bean.validation.test.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.bean.validation.test.BeanValidationTestContainer;

class TemporalValidatorTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(DateHolder.class, LocalDateHolder.class, InstantHolder.class,
                    LocalDateTimeHolder.class, ZonedDateTimeHolder.class, OffsetDateTimeHolder.class,
                    YearHolder.class, YearMonthHolder.class, LocalTimeHolder.class)
            .build();

    // --- java.util.Date and Calendar ---

    static class DateHolder {
        @Past
        Calendar calendarWithPastDate;
        @Future
        Calendar calendarWithFutureDate;
        @Past
        Date past;
        @Past
        Date future;
    }

    @Test
    void testFutureAndPastWithDateAndCalendar() {
        Validator validator = container.getValidator();
        DateHolder dateHolder = new DateHolder();

        Calendar pastCal = Calendar.getInstance();
        pastCal.add(Calendar.YEAR, -1);
        dateHolder.calendarWithPastDate = pastCal;
        dateHolder.past = pastCal.getTime();

        Calendar futureCal = Calendar.getInstance();
        futureCal.add(Calendar.YEAR, 1);
        dateHolder.calendarWithFutureDate = futureCal;
        dateHolder.future = futureCal.getTime();

        Set<ConstraintViolation<DateHolder>> violations = validator.validate(dateHolder);
        // 'future' field has @Past but holds a future date
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("future");
    }

    // --- LocalDate ---

    static class LocalDateHolder {
        @Past
        LocalDate past;
        @Future
        LocalDate future;
        @PastOrPresent
        LocalDate pastOrPresent;
        @FutureOrPresent
        LocalDate futureOrPresent;
    }

    @Test
    void testPastLocalDate() {
        Validator validator = container.getValidator();
        LocalDateHolder holder = new LocalDateHolder();
        holder.past = LocalDate.now().minusDays(1);
        holder.future = LocalDate.now().plusDays(1);
        holder.pastOrPresent = LocalDate.now();
        holder.futureOrPresent = LocalDate.now();

        assertThat(validator.validate(holder)).isEmpty();
    }

    @Test
    void testPastLocalDateViolation() {
        Validator validator = container.getValidator();
        LocalDateHolder holder = new LocalDateHolder();
        holder.past = LocalDate.now().plusDays(1); // future, not past
        holder.future = LocalDate.now().plusDays(1);
        holder.pastOrPresent = LocalDate.now();
        holder.futureOrPresent = LocalDate.now();

        Set<ConstraintViolation<LocalDateHolder>> violations = validator.validate(holder);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("past");
    }

    @Test
    void testNullTemporalIsValid() {
        Validator validator = container.getValidator();
        LocalDateHolder holder = new LocalDateHolder();
        // All null - should be valid per spec
        assertThat(validator.validate(holder)).isEmpty();
    }

    // --- Instant ---

    static class InstantHolder {
        @Past
        Instant past;
        @Future
        Instant future;
    }

    @Test
    void testInstantConstraints() {
        Validator validator = container.getValidator();
        InstantHolder holder = new InstantHolder();
        holder.past = Instant.now().minusSeconds(60);
        holder.future = Instant.now().plusSeconds(60);

        assertThat(validator.validate(holder)).isEmpty();
    }

    @Test
    void testInstantViolation() {
        Validator validator = container.getValidator();
        InstantHolder holder = new InstantHolder();
        holder.past = Instant.now().plusSeconds(60); // future, not past
        holder.future = Instant.now().plusSeconds(60);

        Set<ConstraintViolation<InstantHolder>> violations = validator.validate(holder);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("past");
    }

    // --- LocalDateTime ---

    static class LocalDateTimeHolder {
        @Past
        LocalDateTime past;
        @Future
        LocalDateTime future;
    }

    @Test
    void testLocalDateTimeConstraints() {
        Validator validator = container.getValidator();
        LocalDateTimeHolder holder = new LocalDateTimeHolder();
        holder.past = LocalDateTime.now().minusDays(1);
        holder.future = LocalDateTime.now().plusDays(1);

        assertThat(validator.validate(holder)).isEmpty();
    }

    // --- ZonedDateTime ---

    static class ZonedDateTimeHolder {
        @Past
        ZonedDateTime past;
        @Future
        ZonedDateTime future;
    }

    @Test
    void testZonedDateTimeConstraints() {
        Validator validator = container.getValidator();
        ZonedDateTimeHolder holder = new ZonedDateTimeHolder();
        holder.past = ZonedDateTime.now().minusDays(1);
        holder.future = ZonedDateTime.now().plusDays(1);

        assertThat(validator.validate(holder)).isEmpty();
    }

    // --- OffsetDateTime ---

    static class OffsetDateTimeHolder {
        @Past
        OffsetDateTime past;
        @Future
        OffsetDateTime future;
    }

    @Test
    void testOffsetDateTimeConstraints() {
        Validator validator = container.getValidator();
        OffsetDateTimeHolder holder = new OffsetDateTimeHolder();
        holder.past = OffsetDateTime.now().minusDays(1);
        holder.future = OffsetDateTime.now().plusDays(1);

        assertThat(validator.validate(holder)).isEmpty();
    }

    // --- Year ---

    static class YearHolder {
        @Past
        Year past;
        @Future
        Year future;
    }

    @Test
    void testYearConstraints() {
        Validator validator = container.getValidator();
        YearHolder holder = new YearHolder();
        holder.past = Year.now().minusYears(1);
        holder.future = Year.now().plusYears(1);

        assertThat(validator.validate(holder)).isEmpty();
    }

    // --- YearMonth ---

    static class YearMonthHolder {
        @Past
        YearMonth past;
        @Future
        YearMonth future;
    }

    @Test
    void testYearMonthConstraints() {
        Validator validator = container.getValidator();
        YearMonthHolder holder = new YearMonthHolder();
        holder.past = YearMonth.now().minusMonths(1);
        holder.future = YearMonth.now().plusMonths(1);

        assertThat(validator.validate(holder)).isEmpty();
    }

    // --- LocalTime ---

    static class LocalTimeHolder {
        @Past
        LocalTime past;
        @Future
        LocalTime future;
    }

    @Test
    void testLocalTimeConstraints() {
        Validator validator = container.getValidator();
        LocalTimeHolder holder = new LocalTimeHolder();
        holder.past = LocalTime.now().minusHours(1);
        holder.future = LocalTime.now().plusHours(1);

        assertThat(validator.validate(holder)).isEmpty();
    }
}
