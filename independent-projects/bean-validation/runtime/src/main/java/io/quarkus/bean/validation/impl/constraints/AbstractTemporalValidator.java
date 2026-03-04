package io.quarkus.bean.validation.impl.constraints;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.util.Calendar;
import java.util.Date;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Base class for temporal validators (Past, PastOrPresent, Future, FutureOrPresent).
 *
 * @param <A> the constraint annotation type
 * @param <T> the validated temporal type
 */
abstract class AbstractTemporalValidator<A extends Annotation, T> implements ConstraintValidator<A, T> {

    /**
     * Defines the comparison mode for the temporal check.
     */
    enum TemporalMode {
        /** Value must be strictly in the past */
        PAST,
        /** Value must be in the past or present */
        PAST_OR_PRESENT,
        /** Value must be strictly in the future */
        FUTURE,
        /** Value must be in the future or present */
        FUTURE_OR_PRESENT
    }

    private final TemporalMode mode;

    protected AbstractTemporalValidator(TemporalMode mode) {
        this.mode = mode;
    }

    @Override
    public void initialize(A constraintAnnotation) {
    }

    protected Clock getClock(ConstraintValidatorContext context) {
        try {
            return context.getClockProvider().getClock();
        } catch (jakarta.validation.ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new jakarta.validation.ValidationException("Exception in ClockProvider", e);
        }
    }

    /**
     * Tests whether the comparison result satisfies the temporal mode.
     *
     * @param comparison negative if value is before now, zero if equal, positive if after
     * @return true if valid
     */
    protected boolean isValidComparison(int comparison) {
        switch (mode) {
            case PAST:
                return comparison < 0;
            case PAST_OR_PRESENT:
                return comparison <= 0;
            case FUTURE:
                return comparison > 0;
            case FUTURE_OR_PRESENT:
                return comparison >= 0;
            default:
                return false;
        }
    }

    // ---- Concrete inner base classes for each temporal type ----

    static abstract class ForDate<A extends Annotation> extends AbstractTemporalValidator<A, Date> {
        protected ForDate(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Date value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            Instant now = getClock(context).instant();
            return isValidComparison(value.toInstant().compareTo(now));
        }
    }

    static abstract class ForCalendar<A extends Annotation> extends AbstractTemporalValidator<A, Calendar> {
        protected ForCalendar(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Calendar value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            Instant now = getClock(context).instant();
            return isValidComparison(value.toInstant().compareTo(now));
        }
    }

    static abstract class ForInstant<A extends Annotation> extends AbstractTemporalValidator<A, Instant> {
        protected ForInstant(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Instant value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            Instant now = getClock(context).instant();
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForLocalDate<A extends Annotation> extends AbstractTemporalValidator<A, LocalDate> {
        protected ForLocalDate(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            LocalDate now = LocalDate.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForLocalDateTime<A extends Annotation> extends AbstractTemporalValidator<A, LocalDateTime> {
        protected ForLocalDateTime(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            LocalDateTime now = LocalDateTime.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForLocalTime<A extends Annotation> extends AbstractTemporalValidator<A, LocalTime> {
        protected ForLocalTime(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(LocalTime value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            LocalTime now = LocalTime.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForMonthDay<A extends Annotation> extends AbstractTemporalValidator<A, MonthDay> {
        protected ForMonthDay(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(MonthDay value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            MonthDay now = MonthDay.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForOffsetDateTime<A extends Annotation> extends AbstractTemporalValidator<A, OffsetDateTime> {
        protected ForOffsetDateTime(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(OffsetDateTime value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            OffsetDateTime now = OffsetDateTime.now(getClock(context));
            return isValidComparison(value.toInstant().compareTo(now.toInstant()));
        }
    }

    static abstract class ForOffsetTime<A extends Annotation> extends AbstractTemporalValidator<A, OffsetTime> {
        protected ForOffsetTime(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(OffsetTime value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            OffsetTime now = OffsetTime.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForYear<A extends Annotation> extends AbstractTemporalValidator<A, Year> {
        protected ForYear(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(Year value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            Year now = Year.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForYearMonth<A extends Annotation> extends AbstractTemporalValidator<A, YearMonth> {
        protected ForYearMonth(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(YearMonth value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            YearMonth now = YearMonth.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForZonedDateTime<A extends Annotation> extends AbstractTemporalValidator<A, ZonedDateTime> {
        protected ForZonedDateTime(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(ZonedDateTime value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            ZonedDateTime now = ZonedDateTime.now(getClock(context));
            return isValidComparison(value.toInstant().compareTo(now.toInstant()));
        }
    }

    static abstract class ForHijrahDate<A extends Annotation> extends AbstractTemporalValidator<A, HijrahDate> {
        protected ForHijrahDate(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(HijrahDate value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            HijrahDate now = HijrahDate.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForJapaneseDate<A extends Annotation> extends AbstractTemporalValidator<A, JapaneseDate> {
        protected ForJapaneseDate(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(JapaneseDate value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            JapaneseDate now = JapaneseDate.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForMinguoDate<A extends Annotation> extends AbstractTemporalValidator<A, MinguoDate> {
        protected ForMinguoDate(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(MinguoDate value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            MinguoDate now = MinguoDate.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }

    static abstract class ForThaiBuddhistDate<A extends Annotation> extends AbstractTemporalValidator<A, ThaiBuddhistDate> {
        protected ForThaiBuddhistDate(TemporalMode mode) {
            super(mode);
        }

        @Override
        public boolean isValid(ThaiBuddhistDate value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            ThaiBuddhistDate now = ThaiBuddhistDate.now(getClock(context));
            return isValidComparison(value.compareTo(now));
        }
    }
}
