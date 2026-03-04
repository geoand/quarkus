package io.quarkus.bean.validator.runtime;

import java.util.IdentityHashMap;
import java.util.Map;

import jakarta.enterprise.context.Dependent;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ValidationException;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.bean.validation.ValidatorInstantiator;

public class ArcConstraintValidatorFactory implements ConstraintValidatorFactory {

    private final Map<ConstraintValidator<?, ?>, InstanceHandle<?>> destroyableConstraintValidators = new IdentityHashMap<>();
    private final ValidatorInstantiator validatorInstantiator;

    public ArcConstraintValidatorFactory(ValidatorInstantiator validatorInstantiator) {
        this.validatorInstantiator = validatorInstantiator;
    }

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
        InstanceHandle<T> handle = Arc.container().instance(key);
        if (handle.isAvailable()) {
            T instance = handle.get();
            if (handle.getBean().getScope().equals(Dependent.class)) {
                destroyableConstraintValidators.put(instance, handle);
            }
            return instance;
        }
        if (validatorInstantiator != null) {
            T instance = validatorInstantiator.getInstance(key);
            if (instance != null) {
                return instance;
            }
        }
        try {
            return key.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ValidationException("Unable to instantiate ConstraintValidator: " + key.getName(), e);
        }
    }

    @Override
    public void releaseInstance(ConstraintValidator<?, ?> instance) {
        InstanceHandle<?> destroyableHandle = destroyableConstraintValidators.remove(instance);
        if (destroyableHandle != null) {
            destroyableHandle.destroy();
        }
    }
}
