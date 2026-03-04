package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Set;

import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import jakarta.validation.metadata.ReturnValueDescriptor;

class ReturnValueDescriptorImpl extends AbstractCascadableDescriptorImpl implements ReturnValueDescriptor {

    ReturnValueDescriptorImpl(Class<?> elementClass,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            boolean isCascaded,
            Set<GroupConversionDescriptor> groupConversions,
            Set<ContainerElementTypeDescriptor> containerElementTypes) {
        super(elementClass, constraintDescriptors, isCascaded, groupConversions, containerElementTypes);
    }
}
