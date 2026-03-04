package io.quarkus.bean.validation.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintTarget;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

/**
 * Resolves which {@code ConstraintValidator} implementation class should be used
 * for a given constraint annotation.
 * <p>
 * Resolution is based on the {@code validatedBy} attribute of the {@code @Constraint}
 * meta-annotation. For built-in constraints the resolver returns {@code null} because
 * the runtime layer uses its own built-in validator registry.
 */
class ValidatorResolver {

    private static final String SVT_PARAMETERS = jakarta.validation.constraintvalidation.ValidationTarget.PARAMETERS.name();
    private static final String SVT_ANNOTATED_ELEMENT = jakarta.validation.constraintvalidation.ValidationTarget.ANNOTATED_ELEMENT
            .name();

    private final IndexView index;

    private final Map<DotName, Map<DotName, String>> builtinValidatorCache = new HashMap<>();

    public ValidatorResolver(IndexView index) {
        this.index = index;
    }

    /**
     * Resolves the validator class for a constraint annotation.
     *
     * @param constraintAnnotation the constraint annotation dot name
     * @param validatedType the type being validated (may be {@code null} when the
     *        exact validated type is not yet known)
     * @return the FQCN of the validator to use, or {@code null} if the validator
     *         should be resolved at runtime (built-in constraints, or when
     *         {@code validatedBy} is empty)
     */
    String resolveValidator(DotName constraintAnnotation, Type validatedType) {
        // For built-in constraints, try to resolve at build time when the validated type is known
        if (DotNames.BUILT_IN_CONSTRAINTS.contains(constraintAnnotation)) {
            if (validatedType == null) {
                return null;
            }
            return resolveBuiltinValidator(constraintAnnotation, validatedType);
        }

        Type[] validators = getValidatedByTypes(constraintAnnotation);
        if (validators == null || validators.length == 0) {
            return null;
        }

        // When a validated type is provided try to find a matching validator
        if (validatedType != null && validators.length > 1) {
            for (Type validatorType : validators) {
                if (validatorMatchesType(validatorType, validatedType)) {
                    return validatorType.name().toString();
                }
            }
        }

        // Fall back to the first declared validator
        return validators[0].name().toString();
    }

    /**
     * Determines the validation target of a constraint annotation.
     *
     * @param constraintAnnotation the constraint annotation dot name
     * @param annotationInstance the usage of the annotation (to check validationAppliesTo attribute)
     * @return the validation target for the constraint
     */
    ValidationTarget determineValidationTarget(DotName constraintAnnotation, AnnotationInstance annotationInstance) {
        if (DotNames.BUILT_IN_CONSTRAINTS.contains(constraintAnnotation)) {
            return ValidationTarget.ANNOTATED_ELEMENT; // Built-in constraints are always generic
        }

        Type[] validators = getValidatedByTypes(constraintAnnotation);
        if (validators == null) {
            return ValidationTarget.ANNOTATED_ELEMENT;
        }

        boolean hasGeneric = false;
        boolean hasCrossParameter = false;

        for (Type validatorType : validators) {
            ClassInfo validatorClass = index.getClassByName(validatorType.name());
            if (validatorClass == null) {
                hasGeneric = true; // assume generic if we can't inspect
                continue;
            }

            AnnotationInstance svtAnn = validatorClass.annotation(DotNames.SUPPORTED_VALIDATION_TARGET);
            if (svtAnn == null) {
                hasGeneric = true; // no annotation = generic (ANNOTATED_ELEMENT)
            } else {
                AnnotationValue valueAttr = svtAnn.value();
                if (valueAttr != null) {
                    String[] targets = valueAttr.asEnumArray();
                    for (String target : targets) {
                        if (SVT_PARAMETERS.equals(target)) {
                            hasCrossParameter = true;
                        }
                        if (SVT_ANNOTATED_ELEMENT.equals(target)) {
                            hasGeneric = true;
                        }
                    }
                }
            }
        }

        if (hasGeneric && hasCrossParameter) {
            // Check validationAppliesTo attribute on the usage
            if (annotationInstance != null) {
                AnnotationValue vatAttr = annotationInstance.value("validationAppliesTo");
                if (vatAttr != null) {
                    String vatValue = vatAttr.asEnum();
                    if (ConstraintTarget.PARAMETERS.name().equals(vatValue)) {
                        return ValidationTarget.PARAMETERS;
                    }
                    if (ConstraintTarget.RETURN_VALUE.name().equals(vatValue)) {
                        return ValidationTarget.ANNOTATED_ELEMENT;
                    }
                }
            }
            return ValidationTarget.BOTH; // Implicit - determined at runtime based on context
        }

        if (hasCrossParameter) {
            return ValidationTarget.PARAMETERS;
        }
        return ValidationTarget.ANNOTATED_ELEMENT;
    }

    List<String> resolveAllValidators(DotName constraintAnnotation) {
        if (DotNames.BUILT_IN_CONSTRAINTS.contains(constraintAnnotation)) {
            return Collections.emptyList(); // Handled by built-in registry at runtime
        }

        Type[] validators = getValidatedByTypes(constraintAnnotation);
        if (validators == null || validators.length == 0) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>(validators.length);
        for (Type validatorType : validators) {
            result.add(validatorType.name().toString());
        }
        return result;
    }

    /**
     * Looks up the {@code validatedBy} attribute of the {@code @Constraint}
     * meta-annotation on the given constraint annotation class.
     *
     * @return the validator types array, or {@code null} if the annotation class,
     *         {@code @Constraint} meta-annotation, or {@code validatedBy} attribute
     *         is not found in the index
     */
    private Type[] getValidatedByTypes(DotName constraintAnnotation) {
        ClassInfo annotationClass = index.getClassByName(constraintAnnotation);
        if (annotationClass == null) {
            return null;
        }

        AnnotationInstance constraintMeta = annotationClass.annotation(DotNames.CONSTRAINT);
        if (constraintMeta == null) {
            return null;
        }

        AnnotationValue validatedBy = constraintMeta.value("validatedBy");
        if (validatedBy == null) {
            return null;
        }

        return validatedBy.asClassArray();
    }

    /**
     * Resolves a built-in validator for the given constraint annotation and validated type
     * using the Jandex index. Looks up all implementors of ConstraintValidator whose first
     * type argument matches the constraint annotation, then finds one whose second type
     * argument matches the validated type.
     */
    private String resolveBuiltinValidator(DotName constraintAnnotation, Type validatedType) {
        Map<DotName, String> typeToValidator = builtinValidatorCache.computeIfAbsent(constraintAnnotation, k -> {
            Map<DotName, String> map = new HashMap<>();
            Collection<ClassInfo> implementors = index.getAllKnownImplementations(DotNames.CONSTRAINT_VALIDATOR);
            for (ClassInfo impl : implementors) {
                for (Type iface : impl.interfaceTypes()) {
                    if (iface.kind() == Type.Kind.PARAMETERIZED_TYPE
                            && iface.name().equals(DotNames.CONSTRAINT_VALIDATOR)) {
                        List<Type> typeArgs = iface.asParameterizedType().arguments();
                        if (typeArgs.size() == 2 && typeArgs.get(0).name().equals(k)) {
                            map.put(typeArgs.get(1).name(), impl.name().toString());
                        }
                    }
                }
            }
            return map;
        });

        // 1. Exact match by DotName
        String result = typeToValidator.get(validatedType.name());
        if (result != null) {
            return result;
        }

        // 2. Walk superclass hierarchy of the validated type using Jandex
        ClassInfo current = index.getClassByName(validatedType.name());
        if (current != null) {
            Type superType = current.superClassType();
            while (superType != null && !superType.name().equals(DotNames.OBJECT)) {
                result = typeToValidator.get(superType.name());
                if (result != null) {
                    return result;
                }
                ClassInfo superClass = index.getClassByName(superType.name());
                superType = superClass != null ? superClass.superClassType() : null;
            }
            // 3. Check interfaces
            for (Type ifaceType : current.interfaceTypes()) {
                result = typeToValidator.get(ifaceType.name());
                if (result != null) {
                    return result;
                }
            }
        }

        // No match found — runtime will handle it
        return null;
    }

    /**
     * Attempts a simple name-based match between a validator class and the
     * validated type. A full generic-signature check would require class hierarchy
     * resolution, so this is intentionally conservative; if it cannot confirm a
     * match it returns {@code false} and the caller falls back to the first
     * validator.
     */
    private boolean validatorMatchesType(Type validatorType, Type validatedType) {
        ClassInfo validatorClass = index.getClassByName(validatorType.name());
        if (validatorClass == null) {
            return false;
        }

        // Look at the ConstraintValidator<A, T> interface to extract T
        for (Type iface : validatorClass.interfaceTypes()) {
            if (iface.kind() == Type.Kind.PARAMETERIZED_TYPE
                    && iface.name().toString().equals("jakarta.validation.ConstraintValidator")) {
                List<Type> typeArgs = iface.asParameterizedType().arguments();
                if (typeArgs.size() == 2) {
                    Type validatorTargetType = typeArgs.get(1);
                    if (validatorTargetType.name().equals(validatedType.name())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
