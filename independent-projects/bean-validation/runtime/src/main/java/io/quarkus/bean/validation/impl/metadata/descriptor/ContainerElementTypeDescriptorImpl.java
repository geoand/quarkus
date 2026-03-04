package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Set;

import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;

class ContainerElementTypeDescriptorImpl extends AbstractCascadableDescriptorImpl
        implements ContainerElementTypeDescriptor {

    private final int typeArgumentIndex;

    ContainerElementTypeDescriptorImpl(Class<?> containerClass,
            int typeArgumentIndex,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            boolean isCascaded,
            Set<GroupConversionDescriptor> groupConversions) {
        super(containerClass, constraintDescriptors, isCascaded, groupConversions, null);
        this.typeArgumentIndex = typeArgumentIndex;
    }

    @Override
    public Class<?> getContainerClass() {
        return getElementClass();
    }

    @Override
    public Integer getTypeArgumentIndex() {
        return typeArgumentIndex;
    }
}
