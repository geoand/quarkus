package io.quarkus.jackson.spi;

import org.jboss.jandex.ClassInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item that nominates a type for precomputed Jackson metadata.
 * When a type is nominated, its field/method/constructor metadata is extracted
 * at build time via Jandex and used to create a {@code PrecomputedBeanDescription}
 * at runtime, bypassing Jackson's reflection-based introspection.
 */
public final class PrecomputedJacksonTypeBuildItem extends MultiBuildItem {

    private final ClassInfo classInfo;

    public PrecomputedJacksonTypeBuildItem(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public ClassInfo getClassInfo() {
        return classInfo;
    }
}
