package io.quarkus.bean.validation.impl.constraints;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintValidator;

public final class BuiltinConstraintValidators {

    private static final Map<String, List<Class<? extends ConstraintValidator<?, ?>>>> VALIDATORS = new HashMap<>();
    private static final Map<String, Map<Class<?>, Class<? extends ConstraintValidator<?, ?>>>> TYPE_TO_VALIDATOR_CACHE = new HashMap<>();

    // Inner class names shared by NotEmpty and Size validators
    private static final String[] COLLECTION_LIKE_SUFFIXES = {
            "ForCharSequence", "ForCollection", "ForMap", "ForArray",
            "ForBooleanArray", "ForByteArray", "ForCharArray",
            "ForDoubleArray", "ForFloatArray", "ForIntArray",
            "ForLongArray", "ForShortArray"
    };

    // Inner class names shared by Min, Max, DecimalMin, DecimalMax, Digits validators
    private static final String[] NUMERIC_SUFFIXES = {
            "ForNumber", "ForBigDecimal", "ForBigInteger",
            "ForLong", "ForInteger", "ForShort", "ForByte",
            "ForDouble", "ForFloat", "ForCharSequence"
    };

    // Inner class names shared by Positive, PositiveOrZero, Negative, NegativeOrZero validators
    private static final String[] SIGN_SUFFIXES = {
            "ForNumber", "ForBigDecimal", "ForBigInteger",
            "ForLong", "ForInteger", "ForShort", "ForByte",
            "ForDouble", "ForFloat"
    };

    // Inner class names shared by Past, PastOrPresent, Future, FutureOrPresent validators
    private static final String[] TEMPORAL_SUFFIXES = {
            "ForDate", "ForCalendar", "ForInstant",
            "ForLocalDate", "ForLocalDateTime", "ForLocalTime", "ForMonthDay",
            "ForOffsetDateTime", "ForOffsetTime",
            "ForYear", "ForYearMonth", "ForZonedDateTime",
            "ForHijrahDate", "ForJapaneseDate", "ForMinguoDate", "ForThaiBuddhistDate"
    };

    static {
        // AssertTrue / AssertFalse
        register("jakarta.validation.constraints.AssertTrue",
                AssertTrueValidator.class);
        register("jakarta.validation.constraints.AssertFalse",
                AssertFalseValidator.class);

        // Null / NotNull
        register("jakarta.validation.constraints.Null",
                NullValidator.class);
        register("jakarta.validation.constraints.NotNull",
                NotNullValidator.class);

        // NotBlank
        register("jakarta.validation.constraints.NotBlank",
                NotBlankValidator.class);

        // Pattern
        register("jakarta.validation.constraints.Pattern",
                PatternValidator.class);

        // Email
        register("jakarta.validation.constraints.Email",
                EmailValidator.class);

        // Collection-like validators (NotEmpty, Size)
        registerByInnerClasses("jakarta.validation.constraints.NotEmpty",
                NotEmptyValidator.class, COLLECTION_LIKE_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.Size",
                SizeValidator.class, COLLECTION_LIKE_SUFFIXES);

        // Numeric validators (Min, Max, DecimalMin, DecimalMax, Digits)
        registerByInnerClasses("jakarta.validation.constraints.Min",
                MinValidator.class, NUMERIC_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.Max",
                MaxValidator.class, NUMERIC_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.DecimalMin",
                DecimalMinValidator.class, NUMERIC_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.DecimalMax",
                DecimalMaxValidator.class, NUMERIC_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.Digits",
                DigitsValidator.class, NUMERIC_SUFFIXES);

        // Sign validators (Positive, PositiveOrZero, Negative, NegativeOrZero)
        registerByInnerClasses("jakarta.validation.constraints.Positive",
                PositiveValidator.class, SIGN_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.PositiveOrZero",
                PositiveOrZeroValidator.class, SIGN_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.Negative",
                NegativeValidator.class, SIGN_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.NegativeOrZero",
                NegativeOrZeroValidator.class, SIGN_SUFFIXES);

        // Temporal validators (Past, PastOrPresent, Future, FutureOrPresent)
        registerByInnerClasses("jakarta.validation.constraints.Past",
                PastValidator.class, TEMPORAL_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.PastOrPresent",
                PastOrPresentValidator.class, TEMPORAL_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.Future",
                FutureValidator.class, TEMPORAL_SUFFIXES);
        registerByInnerClasses("jakarta.validation.constraints.FutureOrPresent",
                FutureOrPresentValidator.class, TEMPORAL_SUFFIXES);
    }

    private BuiltinConstraintValidators() {
    }

    @SuppressWarnings("unchecked")
    private static void register(String annotationClass, Class<?>... validatorClasses) {
        List<Class<? extends ConstraintValidator<?, ?>>> list = new ArrayList<>(validatorClasses.length);
        for (Class<?> validatorClass : validatorClasses) {
            list.add((Class<? extends ConstraintValidator<?, ?>>) validatorClass);
        }
        VALIDATORS.put(annotationClass, List.copyOf(list));
    }

    private static void registerByInnerClasses(String annotationClass, Class<?> outerClass, String[] suffixes) {
        Class<?>[] innerClasses = new Class<?>[suffixes.length];
        for (int i = 0; i < suffixes.length; i++) {
            try {
                innerClasses[i] = Class.forName(outerClass.getName() + "$" + suffixes[i]);
            } catch (ClassNotFoundException e) {
                throw new ExceptionInInitializerError(
                        "Missing inner class " + outerClass.getName() + "$" + suffixes[i]);
            }
        }
        register(annotationClass, innerClasses);
    }

    public static List<Class<? extends ConstraintValidator<?, ?>>> getValidators(String constraintAnnotationClassName) {
        return VALIDATORS.getOrDefault(constraintAnnotationClassName, Collections.emptyList());
    }

    public static Set<String> getAllConstraintAnnotationNames() {
        return Collections.unmodifiableSet(VALIDATORS.keySet());
    }

    /**
     * Returns the class names of all registered built-in constraint validator classes.
     * This is used by the Quarkus deployment processor to register these classes for
     * reflection in native image builds.
     *
     * @return a list of all built-in validator class names
     */
    public static List<String> getAllValidatorClassNames() {
        List<String> result = new ArrayList<>();
        for (List<Class<? extends ConstraintValidator<?, ?>>> validators : VALIDATORS.values()) {
            for (Class<? extends ConstraintValidator<?, ?>> validatorClass : validators) {
                result.add(validatorClass.getName());
            }
        }
        return result;
    }

    /**
     * Finds the best matching validator for a given constraint annotation and validated type.
     * Resolution order:
     * <ol>
     * <li>Exact type match on the validated type</li>
     * <li>Walk up the superclass hierarchy</li>
     * <li>Check implemented interfaces</li>
     * <li>Fall back to the first validator that accepts {@link Object}</li>
     * </ol>
     *
     * @param constraintAnnotationClassName the fully qualified class name of the constraint annotation
     * @param validatedType the type being validated
     * @return the best matching validator class, or {@code null} if no suitable validator is found
     */
    public static Class<? extends ConstraintValidator<?, ?>> findValidator(String constraintAnnotationClassName,
            Class<?> validatedType) {
        List<Class<? extends ConstraintValidator<?, ?>>> validators = getValidators(constraintAnnotationClassName);
        if (validators.isEmpty()) {
            return null;
        }

        // Build a map from validated type -> validator class for efficient lookup (cached)
        Map<Class<?>, Class<? extends ConstraintValidator<?, ?>>> typeToValidator = TYPE_TO_VALIDATOR_CACHE
                .computeIfAbsent(constraintAnnotationClassName, k -> {
                    Map<Class<?>, Class<? extends ConstraintValidator<?, ?>>> map = new HashMap<>();
                    for (Class<? extends ConstraintValidator<?, ?>> vc : validators) {
                        Class<?> extractedType = extractValidatedType(vc);
                        if (extractedType != null) {
                            map.put(extractedType, vc);
                        }
                    }
                    return map;
                });

        // 1. Exact match
        Class<? extends ConstraintValidator<?, ?>> result = typeToValidator.get(validatedType);
        if (result != null) {
            return result;
        }

        // 2. Walk up superclass hierarchy
        Class<?> current = validatedType.getSuperclass();
        while (current != null && current != Object.class) {
            result = typeToValidator.get(current);
            if (result != null) {
                return result;
            }
            current = current.getSuperclass();
        }

        // 3. Check implemented interfaces (breadth-first)
        for (Class<?> iface : getAllInterfaces(validatedType)) {
            result = typeToValidator.get(iface);
            if (result != null) {
                return result;
            }
        }

        // 3.5. For array types, check Object[] validator (all reference arrays are subtypes of Object[])
        if (validatedType.isArray() && !validatedType.getComponentType().isPrimitive()) {
            result = typeToValidator.get(Object[].class);
            if (result != null) {
                return result;
            }
        }

        // 4. Fall back to Object validator
        result = typeToValidator.get(Object.class);
        return result;
    }

    /**
     * Extracts the validated type (second type parameter) from a ConstraintValidator implementation.
     * Handles multi-level generic inheritance chains like:
     * {@code PositiveValidator.ForInteger -> AbstractNumberSignValidator.ForInteger<Positive>
     *     -> AbstractNumberSignValidator<Positive, Integer> -> ConstraintValidator<Positive, Integer>}
     */
    private static Class<?> extractValidatedType(Class<? extends ConstraintValidator<?, ?>> validatorClass) {
        return extractValidatedTypeRecursive(validatorClass, new HashMap<>());
    }

    private static Class<?> extractValidatedTypeRecursive(Class<?> clazz, Map<String, Type> typeVarBindings) {
        if (clazz == null || clazz == Object.class) {
            return null;
        }

        // Check all implemented interfaces
        for (Type iface : clazz.getGenericInterfaces()) {
            Class<?> found = resolveConstraintValidatorType(iface, typeVarBindings);
            if (found != null) {
                return found;
            }
        }

        // Check superclass
        Type genericSuper = clazz.getGenericSuperclass();
        if (genericSuper != null) {
            Class<?> found = resolveConstraintValidatorType(genericSuper, typeVarBindings);
            if (found != null) {
                return found;
            }

            // Build new bindings for the superclass's type parameters
            Map<String, Type> newBindings = new HashMap<>(typeVarBindings);
            if (genericSuper instanceof ParameterizedType pt) {
                Class<?> rawSuperClass = (Class<?>) pt.getRawType();
                TypeVariable<?>[] superTypeParams = rawSuperClass.getTypeParameters();
                Type[] actualArgs = pt.getActualTypeArguments();
                for (int i = 0; i < superTypeParams.length && i < actualArgs.length; i++) {
                    Type resolvedArg = resolveType(actualArgs[i], typeVarBindings);
                    newBindings.put(superTypeParams[i].getName(), resolvedArg);
                }
                return extractValidatedTypeRecursive(rawSuperClass, newBindings);
            } else if (genericSuper instanceof Class<?> superClass) {
                return extractValidatedTypeRecursive(superClass, newBindings);
            }
        }

        return null;
    }

    /**
     * Checks if the given type is ConstraintValidator and if so, resolves and returns
     * the second type argument. If the type is a parameterized interface that is NOT
     * ConstraintValidator, recursively walks into that interface's hierarchy.
     */
    private static Class<?> resolveConstraintValidatorType(Type type, Map<String, Type> typeVarBindings) {
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType == ConstraintValidator.class) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 2) {
                    Type resolved = resolveType(typeArgs[1], typeVarBindings);
                    return toClass(resolved);
                }
            }
            // Not ConstraintValidator directly - walk into this interface's hierarchy
            Class<?> rawClass = (Class<?>) rawType;
            Map<String, Type> newBindings = new HashMap<>(typeVarBindings);
            TypeVariable<?>[] ifaceTypeParams = rawClass.getTypeParameters();
            Type[] actualArgs = pt.getActualTypeArguments();
            for (int i = 0; i < ifaceTypeParams.length && i < actualArgs.length; i++) {
                Type resolvedArg = resolveType(actualArgs[i], typeVarBindings);
                newBindings.put(ifaceTypeParams[i].getName(), resolvedArg);
            }
            return extractValidatedTypeRecursive(rawClass, newBindings);
        }
        return null;
    }

    private static Type resolveType(Type type, Map<String, Type> bindings) {
        if (type instanceof TypeVariable<?> tv) {
            Type bound = bindings.get(tv.getName());
            return bound != null ? bound : type;
        }
        return type;
    }

    private static Class<?> toClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawClass) {
                return rawClass;
            }
        }
        if (type instanceof WildcardType wt) {
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0) {
                return toClass(upperBounds[0]);
            }
        }
        return null;
    }

    private static List<Class<?>> getAllInterfaces(Class<?> type) {
        List<Class<?>> result = new ArrayList<>();
        collectInterfaces(type, result);
        return result;
    }

    private static void collectInterfaces(Class<?> type, List<Class<?>> result) {
        if (type == null) {
            return;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (!result.contains(iface)) {
                result.add(iface);
                collectInterfaces(iface, result);
            }
        }
        collectInterfaces(type.getSuperclass(), result);
    }
}
