package io.quarkus.bean.validation.processor;

import java.lang.annotation.Repeatable;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.GroupSequence;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.groups.ConvertGroup;

import org.jboss.jandex.DotName;

final class DotNames {

    private DotNames() {
    }

    static final DotName OBJECT = DotName.createSimple(Object.class);
    static final DotName ITERABLE = DotName.createSimple(Iterable.class);
    static final DotName CONSTRAINT_VALIDATOR = DotName.createSimple(ConstraintValidator.class);

    static final DotName ASSERT_TRUE = DotName.createSimple(AssertTrue.class);
    static final DotName ASSERT_FALSE = DotName.createSimple(AssertFalse.class);
    static final DotName NULL = DotName.createSimple(Null.class);
    static final DotName NOT_NULL = DotName.createSimple(NotNull.class);
    static final DotName NOT_BLANK = DotName.createSimple(NotBlank.class);
    static final DotName NOT_EMPTY = DotName.createSimple(NotEmpty.class);
    static final DotName SIZE = DotName.createSimple(Size.class);
    static final DotName PATTERN = DotName.createSimple(Pattern.class);
    static final DotName EMAIL = DotName.createSimple(Email.class);
    static final DotName MIN = DotName.createSimple(Min.class);
    static final DotName MAX = DotName.createSimple(Max.class);
    static final DotName DECIMAL_MIN = DotName.createSimple(DecimalMin.class);
    static final DotName DECIMAL_MAX = DotName.createSimple(DecimalMax.class);
    static final DotName DIGITS = DotName.createSimple(Digits.class);
    static final DotName POSITIVE = DotName.createSimple(Positive.class);
    static final DotName POSITIVE_OR_ZERO = DotName.createSimple(PositiveOrZero.class);
    static final DotName NEGATIVE = DotName.createSimple(Negative.class);
    static final DotName NEGATIVE_OR_ZERO = DotName.createSimple(NegativeOrZero.class);
    static final DotName PAST = DotName.createSimple(Past.class);
    static final DotName PAST_OR_PRESENT = DotName.createSimple(PastOrPresent.class);
    static final DotName FUTURE = DotName.createSimple(Future.class);
    static final DotName FUTURE_OR_PRESENT = DotName.createSimple(FutureOrPresent.class);

    static final DotName VALID = DotName.createSimple(Valid.class);
    static final DotName CONSTRAINT = DotName.createSimple(Constraint.class);
    static final DotName GROUP_SEQUENCE = DotName.createSimple(GroupSequence.class);
    static final DotName CONVERT_GROUP = DotName.createSimple(ConvertGroup.class);
    static final DotName CONVERT_GROUP_LIST = DotName.createSimple(ConvertGroup.List.class);
    static final DotName REPORT_AS_SINGLE_VIOLATION = DotName.createSimple(ReportAsSingleViolation.class);
    static final DotName OVERRIDES_ATTRIBUTE = DotName.createSimple(OverridesAttribute.class);
    static final DotName OVERRIDES_ATTRIBUTE_LIST = DotName.createSimple(OverridesAttribute.List.class);

    static final DotName SUPPORTED_VALIDATION_TARGET = DotName.createSimple(SupportedValidationTarget.class);

    static final DotName REPEATABLE = DotName.createSimple(Repeatable.class);

    static final Set<DotName> BUILT_IN_CONSTRAINTS = Set.of(
            ASSERT_TRUE, ASSERT_FALSE,
            NULL, NOT_NULL, NOT_BLANK,
            NOT_EMPTY, SIZE, PATTERN,
            EMAIL, MIN, MAX,
            DECIMAL_MIN, DECIMAL_MAX,
            DIGITS, NEGATIVE, POSITIVE,
            POSITIVE_OR_ZERO, NEGATIVE_OR_ZERO,
            PAST, PAST_OR_PRESENT, FUTURE,
            FUTURE_OR_PRESENT);
}
