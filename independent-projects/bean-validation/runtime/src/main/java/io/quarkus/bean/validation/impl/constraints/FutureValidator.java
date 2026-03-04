package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.Future;

public class FutureValidator {

    private FutureValidator() {
    }

    public static class ForDate extends AbstractTemporalValidator.ForDate<Future> {
        public ForDate() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForCalendar extends AbstractTemporalValidator.ForCalendar<Future> {
        public ForCalendar() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForInstant extends AbstractTemporalValidator.ForInstant<Future> {
        public ForInstant() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForLocalDate extends AbstractTemporalValidator.ForLocalDate<Future> {
        public ForLocalDate() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForLocalDateTime extends AbstractTemporalValidator.ForLocalDateTime<Future> {
        public ForLocalDateTime() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForLocalTime extends AbstractTemporalValidator.ForLocalTime<Future> {
        public ForLocalTime() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForMonthDay extends AbstractTemporalValidator.ForMonthDay<Future> {
        public ForMonthDay() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForOffsetDateTime extends AbstractTemporalValidator.ForOffsetDateTime<Future> {
        public ForOffsetDateTime() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForOffsetTime extends AbstractTemporalValidator.ForOffsetTime<Future> {
        public ForOffsetTime() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForYear extends AbstractTemporalValidator.ForYear<Future> {
        public ForYear() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForYearMonth extends AbstractTemporalValidator.ForYearMonth<Future> {
        public ForYearMonth() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForZonedDateTime extends AbstractTemporalValidator.ForZonedDateTime<Future> {
        public ForZonedDateTime() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForHijrahDate extends AbstractTemporalValidator.ForHijrahDate<Future> {
        public ForHijrahDate() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForJapaneseDate extends AbstractTemporalValidator.ForJapaneseDate<Future> {
        public ForJapaneseDate() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForMinguoDate extends AbstractTemporalValidator.ForMinguoDate<Future> {
        public ForMinguoDate() {
            super(TemporalMode.FUTURE);
        }
    }

    public static class ForThaiBuddhistDate extends AbstractTemporalValidator.ForThaiBuddhistDate<Future> {
        public ForThaiBuddhistDate() {
            super(TemporalMode.FUTURE);
        }
    }
}
