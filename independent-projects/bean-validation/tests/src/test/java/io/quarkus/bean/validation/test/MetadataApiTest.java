package io.quarkus.bean.validation.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetadataApiTest {

    @RegisterExtension
    static BeanValidationTestContainer container = BeanValidationTestContainer.builder()
            .beanClasses(MetadataBean.class)
            .build();

    static class MetadataBean {
        @NotNull
        @Size(min = 1, max = 100)
        String name;

        String unconstrained;
    }

    @Test
    void beanDescriptor() {
        BeanDescriptor descriptor = validator().getConstraintsForClass(MetadataBean.class);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.isBeanConstrained()).isTrue();
        assertThat(descriptor.getElementClass()).isEqualTo(MetadataBean.class);
    }

    @Test
    void constrainedProperty() {
        BeanDescriptor descriptor = validator().getConstraintsForClass(MetadataBean.class);
        PropertyDescriptor nameDescriptor = descriptor.getConstraintsForProperty("name");
        assertThat(nameDescriptor).isNotNull();
        assertThat(nameDescriptor.getPropertyName()).isEqualTo("name");
        assertThat(nameDescriptor.hasConstraints()).isTrue();
        assertThat(nameDescriptor.getConstraintDescriptors()).hasSize(2);
    }

    @Test
    void unconstrainedProperty() {
        BeanDescriptor descriptor = validator().getConstraintsForClass(MetadataBean.class);
        PropertyDescriptor unconstrainedDescriptor = descriptor.getConstraintsForProperty("unconstrained");
        assertThat(unconstrainedDescriptor).isNull();
    }

    @Test
    void constrainedProperties() {
        BeanDescriptor descriptor = validator().getConstraintsForClass(MetadataBean.class);
        assertThat(descriptor.getConstrainedProperties()).hasSize(1);
    }

    @Test
    void unconstrainedBean() {
        BeanDescriptor descriptor = validator().getConstraintsForClass(String.class);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.isBeanConstrained()).isFalse();
    }

    private Validator validator() {
        return container.getValidator();
    }
}
