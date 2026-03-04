package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.FutureOrPresent;

public class FutureOrPresentValidator {

    private FutureOrPresentValidator() {
    }

    public static class ForDate extends AbstractTemporalValidator.ForDate<FutureOrPresent> {
        public ForDate() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForCalendar extends AbstractTemporalValidator.ForCalendar<FutureOrPresent> {
        public ForCalendar() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForInstant extends AbstractTemporalValidator.ForInstant<FutureOrPresent> {
        public ForInstant() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForLocalDate extends AbstractTemporalValidator.ForLocalDate<FutureOrPresent> {
        public ForLocalDate() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForLocalDateTime extends AbstractTemporalValidator.ForLocalDateTime<FutureOrPresent> {
        public ForLocalDateTime() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForLocalTime extends AbstractTemporalValidator.ForLocalTime<FutureOrPresent> {
        public ForLocalTime() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForMonthDay extends AbstractTemporalValidator.ForMonthDay<FutureOrPresent> {
        public ForMonthDay() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForOffsetDateTime extends AbstractTemporalValidator.ForOffsetDateTime<FutureOrPresent> {
        public ForOffsetDateTime() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForOffsetTime extends AbstractTemporalValidator.ForOffsetTime<FutureOrPresent> {
        public ForOffsetTime() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForYear extends AbstractTemporalValidator.ForYear<FutureOrPresent> {
        public ForYear() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForYearMonth extends AbstractTemporalValidator.ForYearMonth<FutureOrPresent> {
        public ForYearMonth() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForZonedDateTime extends AbstractTemporalValidator.ForZonedDateTime<FutureOrPresent> {
        public ForZonedDateTime() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForHijrahDate extends AbstractTemporalValidator.ForHijrahDate<FutureOrPresent> {
        public ForHijrahDate() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForJapaneseDate extends AbstractTemporalValidator.ForJapaneseDate<FutureOrPresent> {
        public ForJapaneseDate() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForMinguoDate extends AbstractTemporalValidator.ForMinguoDate<FutureOrPresent> {
        public ForMinguoDate() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }

    public static class ForThaiBuddhistDate extends AbstractTemporalValidator.ForThaiBuddhistDate<FutureOrPresent> {
        public ForThaiBuddhistDate() {
            super(TemporalMode.FUTURE_OR_PRESENT);
        }
    }
}
