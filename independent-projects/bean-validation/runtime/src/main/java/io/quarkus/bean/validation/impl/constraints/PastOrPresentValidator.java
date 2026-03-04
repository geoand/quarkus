package io.quarkus.bean.validation.impl.constraints;

import jakarta.validation.constraints.PastOrPresent;

public class PastOrPresentValidator {

    private PastOrPresentValidator() {
    }

    public static class ForDate extends AbstractTemporalValidator.ForDate<PastOrPresent> {
        public ForDate() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForCalendar extends AbstractTemporalValidator.ForCalendar<PastOrPresent> {
        public ForCalendar() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForInstant extends AbstractTemporalValidator.ForInstant<PastOrPresent> {
        public ForInstant() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForLocalDate extends AbstractTemporalValidator.ForLocalDate<PastOrPresent> {
        public ForLocalDate() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForLocalDateTime extends AbstractTemporalValidator.ForLocalDateTime<PastOrPresent> {
        public ForLocalDateTime() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForLocalTime extends AbstractTemporalValidator.ForLocalTime<PastOrPresent> {
        public ForLocalTime() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForMonthDay extends AbstractTemporalValidator.ForMonthDay<PastOrPresent> {
        public ForMonthDay() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForOffsetDateTime extends AbstractTemporalValidator.ForOffsetDateTime<PastOrPresent> {
        public ForOffsetDateTime() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForOffsetTime extends AbstractTemporalValidator.ForOffsetTime<PastOrPresent> {
        public ForOffsetTime() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForYear extends AbstractTemporalValidator.ForYear<PastOrPresent> {
        public ForYear() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForYearMonth extends AbstractTemporalValidator.ForYearMonth<PastOrPresent> {
        public ForYearMonth() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForZonedDateTime extends AbstractTemporalValidator.ForZonedDateTime<PastOrPresent> {
        public ForZonedDateTime() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForHijrahDate extends AbstractTemporalValidator.ForHijrahDate<PastOrPresent> {
        public ForHijrahDate() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForJapaneseDate extends AbstractTemporalValidator.ForJapaneseDate<PastOrPresent> {
        public ForJapaneseDate() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForMinguoDate extends AbstractTemporalValidator.ForMinguoDate<PastOrPresent> {
        public ForMinguoDate() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }

    public static class ForThaiBuddhistDate extends AbstractTemporalValidator.ForThaiBuddhistDate<PastOrPresent> {
        public ForThaiBuddhistDate() {
            super(TemporalMode.PAST_OR_PRESENT);
        }
    }
}
