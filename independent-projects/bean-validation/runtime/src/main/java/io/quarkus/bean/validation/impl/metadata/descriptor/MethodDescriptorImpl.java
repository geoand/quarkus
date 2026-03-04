package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.List;
import java.util.Set;

import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.CrossParameterDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import jakarta.validation.metadata.ParameterDescriptor;
import jakarta.validation.metadata.ReturnValueDescriptor;

class MethodDescriptorImpl extends AbstractExecutableDescriptorImpl implements MethodDescriptor {

    MethodDescriptorImpl(String name,
            Class<?> elementClass,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            List<ParameterDescriptor> parameterDescriptors,
            ReturnValueDescriptor returnValueDescriptor,
            CrossParameterDescriptor crossParameterDescriptor) {
        super(name, elementClass, constraintDescriptors, parameterDescriptors,
                returnValueDescriptor, crossParameterDescriptor);
    }
}
