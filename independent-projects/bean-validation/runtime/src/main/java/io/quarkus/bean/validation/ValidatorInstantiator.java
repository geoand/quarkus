package io.quarkus.bean.validation;

import jakarta.validation.ConstraintValidator;

/**
 * Creates {@link ConstraintValidator} instances without reflection.
 * Implementations are generated at build time by Gizmo2 and contain
 * a switch over all known validator class names.
 */
public interface ValidatorInstantiator {

    <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key);
}
