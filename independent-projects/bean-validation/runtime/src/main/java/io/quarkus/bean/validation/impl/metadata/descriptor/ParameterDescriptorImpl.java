package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Set;

import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import jakarta.validation.metadata.ParameterDescriptor;

class ParameterDescriptorImpl extends AbstractCascadableDescriptorImpl implements ParameterDescriptor {

    private final String name;
    private final int index;

    ParameterDescriptorImpl(String name,
            int index,
            Class<?> elementClass,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            boolean isCascaded,
            Set<GroupConversionDescriptor> groupConversions,
            Set<ContainerElementTypeDescriptor> containerElementTypes) {
        super(elementClass, constraintDescriptors, isCascaded, groupConversions, containerElementTypes);
        this.name = name;
        this.index = index;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getIndex() {
        return index;
    }
}
