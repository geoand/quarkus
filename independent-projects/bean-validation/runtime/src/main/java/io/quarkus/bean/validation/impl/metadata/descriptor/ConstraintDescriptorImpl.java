package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;
import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ValidateUnwrappedValue;
import jakarta.validation.valueextraction.Unwrapping;

import io.quarkus.bean.validation.QuarkusValidationProvider;
import io.quarkus.bean.validation.impl.constraints.BuiltinConstraintValidators;
import io.quarkus.bean.validation.impl.metadata.model.ConstraintMetadata;

public class ConstraintDescriptorImpl<T extends Annotation> implements ConstraintDescriptor<T> {

    private final T annotation;
    private final Map<String, Object> attributes;
    private final Set<Class<?>> groups;
    private final Set<Class<? extends Payload>> payload;
    private final List<Class<? extends ConstraintValidator<T, ?>>> constraintValidatorClasses;
    private final boolean reportAsSingleViolation;
    private final Set<ConstraintDescriptor<?>> composingConstraints;
    private final String messageTemplate;
    private final ValidateUnwrappedValue valueUnwrapping;
    private final ConstraintTarget validationAppliesTo;

    /**
     * Constructs a new constraint descriptor from build-time metadata.
     *
     * @param annotationType the constraint annotation class
     * @param attributes annotation attribute values
     * @param groupClassNames group class names
     * @param payloadClassNames payload class names
     * @param validatorClassName FQCN of the resolved ConstraintValidator, or {@code null}
     * @param reportAsSingleViolation {@code true} if {@code @ReportAsSingleViolation} is present
     * @param composingConstraintsMeta composing constraint metadata for composed constraints
     */
    @SuppressWarnings("unchecked")
    public ConstraintDescriptorImpl(Class<T> annotationType,
            Map<String, Object> attributes,
            List<String> groupClassNames,
            List<String> payloadClassNames,
            String validatorClassName,
            boolean reportAsSingleViolation,
            List<ConstraintMetadata> composingConstraintsMeta) {

        // Load group classes (needed for annotation proxy)
        Set<Class<?>> groupSet = loadClassSet(groupClassNames, "group");
        // Per spec: if no groups explicitly declared, constraint belongs to Default group
        if (groupSet.isEmpty()) {
            groupSet.add(jakarta.validation.groups.Default.class);
        }
        this.groups = Collections.unmodifiableSet(groupSet);

        // Load payload classes (needed for annotation proxy)
        @SuppressWarnings("unchecked")
        Set<Class<? extends Payload>> payloadSet = (Set<Class<? extends Payload>>) (Set<?>) loadClassSet(
                payloadClassNames, "payload");
        this.payload = Collections.unmodifiableSet(payloadSet);

        // Build the effective attributes map including resolved groups and payload
        // so the annotation proxy reflects inherited values (BV spec 3.3)
        Map<String, Object> effectiveAttributes = attributes != null
                ? new LinkedHashMap<>(attributes)
                : new LinkedHashMap<>();
        effectiveAttributes.put("groups", this.groups.toArray(new Class<?>[0]));
        if (!this.payload.isEmpty()) {
            effectiveAttributes.put("payload", this.payload.toArray(new Class<?>[0]));
        }

        this.annotation = QuarkusValidationProvider.createAnnotationLiteral(annotationType, effectiveAttributes);
        this.attributes = Collections.unmodifiableMap(effectiveAttributes);

        // Resolve constraint validator classes
        this.constraintValidatorClasses = List.copyOf(
                resolveValidatorClasses(annotationType, validatorClassName));

        this.reportAsSingleViolation = reportAsSingleViolation;

        Object msg = this.attributes != null ? this.attributes.get("message") : null;
        this.messageTemplate = msg instanceof String ? (String) msg : null;

        // Determine value unwrapping from payload
        ValidateUnwrappedValue unwrapping = ValidateUnwrappedValue.DEFAULT;
        for (Class<? extends Payload> p : this.payload) {
            if (Unwrapping.Unwrap.class.isAssignableFrom(p)) {
                unwrapping = ValidateUnwrappedValue.UNWRAP;
                break;
            } else if (Unwrapping.Skip.class.isAssignableFrom(p)) {
                unwrapping = ValidateUnwrappedValue.SKIP;
                break;
            }
        }
        this.valueUnwrapping = unwrapping;

        // Determine validationAppliesTo from attributes (computed before composing constraints)
        Object vatObj = this.attributes.get("validationAppliesTo");
        ConstraintTarget resolvedVat;
        if (vatObj instanceof ConstraintTarget ct) {
            resolvedVat = ct;
        } else if (vatObj instanceof String s) {
            try {
                resolvedVat = ConstraintTarget.valueOf(s);
            } catch (IllegalArgumentException e) {
                resolvedVat = ConstraintTarget.IMPLICIT;
            }
        } else {
            resolvedVat = ConstraintTarget.IMPLICIT;
        }
        this.validationAppliesTo = resolvedVat;

        // Recursively build composing constraint descriptors
        // Per BV spec 3.3: composing constraints inherit groups, payload, and
        // validationAppliesTo from the composed constraint
        Set<ConstraintDescriptor<?>> composingSet = new LinkedHashSet<>();
        if (composingConstraintsMeta != null) {
            for (ConstraintMetadata composingMeta : composingConstraintsMeta) {
                try {
                    Class<? extends Annotation> composingAnnotationType = (Class<? extends Annotation>) Class
                            .forName(composingMeta.annotationClassName(), false,
                                    Thread.currentThread().getContextClassLoader());
                    // Start with the composing constraint's own attributes
                    Map<String, Object> composingAttrs = new LinkedHashMap<>(composingMeta.attributes());
                    // Propagate validationAppliesTo to composing constraints if non-IMPLICIT
                    if (this.validationAppliesTo != ConstraintTarget.IMPLICIT) {
                        composingAttrs.put("validationAppliesTo", this.validationAppliesTo);
                    }
                    // Groups and payload inheritance is handled by the constructor
                    // (it puts resolved groups/payload into the effective attributes)
                    composingSet.add(new ConstraintDescriptorImpl<>(
                            composingAnnotationType,
                            composingAttrs,
                            groupClassNames,
                            payloadClassNames,
                            composingMeta.validatorClassName(),
                            composingMeta.reportAsSingleViolation(),
                            composingMeta.getResolvedComposingConstraints()));
                } catch (ClassNotFoundException e) {
                    throw new ValidationException(
                            "Cannot load composing constraint annotation class: "
                                    + composingMeta.annotationClassName(),
                            e);
                }
            }
        }
        this.composingConstraints = Collections.unmodifiableSet(composingSet);
    }

    @Override
    public T getAnnotation() {
        return annotation;
    }

    @Override
    public String getMessageTemplate() {
        return messageTemplate;
    }

    @Override
    public Set<Class<?>> getGroups() {
        return groups;
    }

    @Override
    public Set<Class<? extends Payload>> getPayload() {
        return payload;
    }

    @Override
    public ConstraintTarget getValidationAppliesTo() {
        return validationAppliesTo;
    }

    @Override
    public List<Class<? extends ConstraintValidator<T, ?>>> getConstraintValidatorClasses() {
        return constraintValidatorClasses;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Set<ConstraintDescriptor<?>> getComposingConstraints() {
        return composingConstraints;
    }

    @Override
    public boolean isReportAsSingleViolation() {
        return reportAsSingleViolation;
    }

    @Override
    public ValidateUnwrappedValue getValueUnwrapping() {
        return valueUnwrapping;
    }

    @Override
    public <U> U unwrap(Class<U> type) {
        throw new ValidationException("Cannot unwrap to " + type);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> List<Class<? extends ConstraintValidator<T, ?>>> resolveValidatorClasses(
            Class<T> annotationType, String validatorClassName) {
        List<Class<? extends ConstraintValidator<T, ?>>> validators = new ArrayList<>();

        // First try the single resolved validator (backward compatible)
        if (validatorClassName != null) {
            try {
                Class<?> validatorClass = Class.forName(validatorClassName, false,
                        Thread.currentThread().getContextClassLoader());
                validators.add((Class<? extends ConstraintValidator<T, ?>>) validatorClass);
            } catch (ClassNotFoundException e) {
                throw new ValidationException("Cannot load validator class: " + validatorClassName, e);
            }
        }

        if (validators.isEmpty()) {
            // Look up built-in validators for this annotation type
            List<Class<? extends ConstraintValidator<?, ?>>> builtins = BuiltinConstraintValidators
                    .getValidators(annotationType.getName());
            if (builtins != null) {
                for (Class<? extends ConstraintValidator<?, ?>> builtin : builtins) {
                    validators.add((Class<? extends ConstraintValidator<T, ?>>) builtin);
                }
            }
        }

        return validators;
    }

    private static Set<Class<?>> loadClassSet(List<String> classNames, String errorLabel) {
        Set<Class<?>> result = new LinkedHashSet<>();
        if (classNames != null && !classNames.isEmpty()) {
            for (String className : classNames) {
                try {
                    result.add(Class.forName(className, false,
                            Thread.currentThread().getContextClassLoader()));
                } catch (ClassNotFoundException e) {
                    throw new ValidationException("Cannot load " + errorLabel + " class: " + className, e);
                }
            }
        }
        return result;
    }
}
