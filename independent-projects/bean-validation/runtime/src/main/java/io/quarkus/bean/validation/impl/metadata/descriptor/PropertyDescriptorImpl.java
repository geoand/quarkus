package io.quarkus.bean.validation.impl.metadata.descriptor;

import java.util.Set;

import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;

class PropertyDescriptorImpl extends AbstractCascadableDescriptorImpl implements PropertyDescriptor {

    private final String propertyName;

    PropertyDescriptorImpl(Class<?> elementClass,
            String propertyName,
            Set<ConstraintDescriptor<?>> constraintDescriptors,
            boolean isCascaded,
            Set<GroupConversionDescriptor> groupConversions,
            Set<ContainerElementTypeDescriptor> containerElementTypes) {
        super(elementClass, constraintDescriptors, isCascaded, groupConversions, containerElementTypes);
        this.propertyName = propertyName;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }
}
