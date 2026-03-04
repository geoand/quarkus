package io.quarkus.bean.validation.impl.metadata.descriptor;

import jakarta.validation.metadata.GroupConversionDescriptor;

class GroupConversionDescriptorImpl implements GroupConversionDescriptor {

    private final Class<?> from;
    private final Class<?> to;

    GroupConversionDescriptorImpl(Class<?> from, Class<?> to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Class<?> getFrom() {
        return from;
    }

    @Override
    public Class<?> getTo() {
        return to;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroupConversionDescriptorImpl that = (GroupConversionDescriptorImpl) o;
        return from.equals(that.from) && to.equals(that.to);
    }

    @Override
    public int hashCode() {
        int result = from.hashCode();
        result = 31 * result + to.hashCode();
        return result;
    }
}
