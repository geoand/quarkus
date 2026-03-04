package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.Past;

public class PastValidator {

    private PastValidator() {
    }

    public static class ForDate extends AbstractTemporalValidator.ForDate<Past> {
        public ForDate() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForCalendar extends AbstractTemporalValidator.ForCalendar<Past> {
        public ForCalendar() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForInstant extends AbstractTemporalValidator.ForInstant<Past> {
        public ForInstant() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForLocalDate extends AbstractTemporalValidator.ForLocalDate<Past> {
        public ForLocalDate() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForLocalDateTime extends AbstractTemporalValidator.ForLocalDateTime<Past> {
        public ForLocalDateTime() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForLocalTime extends AbstractTemporalValidator.ForLocalTime<Past> {
        public ForLocalTime() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForMonthDay extends AbstractTemporalValidator.ForMonthDay<Past> {
        public ForMonthDay() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForOffsetDateTime extends AbstractTemporalValidator.ForOffsetDateTime<Past> {
        public ForOffsetDateTime() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForOffsetTime extends AbstractTemporalValidator.ForOffsetTime<Past> {
        public ForOffsetTime() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForYear extends AbstractTemporalValidator.ForYear<Past> {
        public ForYear() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForYearMonth extends AbstractTemporalValidator.ForYearMonth<Past> {
        public ForYearMonth() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForZonedDateTime extends AbstractTemporalValidator.ForZonedDateTime<Past> {
        public ForZonedDateTime() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForHijrahDate extends AbstractTemporalValidator.ForHijrahDate<Past> {
        public ForHijrahDate() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForJapaneseDate extends AbstractTemporalValidator.ForJapaneseDate<Past> {
        public ForJapaneseDate() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForMinguoDate extends AbstractTemporalValidator.ForMinguoDate<Past> {
        public ForMinguoDate() {
            super(TemporalMode.PAST);
        }
    }

    public static class ForThaiBuddhistDate extends AbstractTemporalValidator.ForThaiBuddhistDate<Past> {
        public ForThaiBuddhistDate() {
            super(TemporalMode.PAST);
        }
    }
}
