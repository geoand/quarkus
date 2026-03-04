package io.quarkus.bean.validation.impl;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;

public class QuarkusConstraintValidatorContext implements ConstraintValidatorContext {

    private final PathImpl basePath;
    private final ClockProvider clockProvider;
    private final ElementKind constraintKind;
    private final List<String> parameterNames;
    private final String defaultMessageTemplate;
    private boolean defaultDisabled;
    private final List<ViolationEntry> customViolations = new ArrayList<>();

    public QuarkusConstraintValidatorContext(ConstraintDescriptor<?> constraintDescriptor,
            PathImpl basePath, ClockProvider clockProvider) {
        this(constraintDescriptor, basePath, clockProvider, null, null);
    }

    public QuarkusConstraintValidatorContext(ConstraintDescriptor<?> constraintDescriptor,
            PathImpl basePath, ClockProvider clockProvider, ElementKind constraintKind) {
        this(constraintDescriptor, basePath, clockProvider, constraintKind, null);
    }

    public QuarkusConstraintValidatorContext(ConstraintDescriptor<?> constraintDescriptor,
            PathImpl basePath, ClockProvider clockProvider, ElementKind constraintKind,
            List<String> parameterNames) {
        this.basePath = basePath;
        this.clockProvider = clockProvider;
        this.constraintKind = constraintKind;
        this.parameterNames = parameterNames;
        this.defaultMessageTemplate = (String) constraintDescriptor.getAttributes().get("message");
    }

    @Override
    public void disableDefaultConstraintViolation() {
        defaultDisabled = true;
    }

    @Override
    public String getDefaultConstraintMessageTemplate() {
        return defaultMessageTemplate;
    }

    @Override
    public ConstraintViolationBuilder buildConstraintViolationWithTemplate(String messageTemplate) {
        return new ConstraintViolationBuilderImpl(messageTemplate);
    }

    @Override
    public ClockProvider getClockProvider() {
        return clockProvider;
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(QuarkusConstraintValidatorContext.class)) {
            return type.cast(this);
        }
        throw new jakarta.validation.ValidationException("Cannot unwrap to " + type);
    }

    public boolean isDefaultDisabled() {
        return defaultDisabled;
    }

    public List<ViolationEntry> getCustomViolations() {
        return customViolations;
    }

    public PathImpl getBasePath() {
        return basePath;
    }

    public record ViolationEntry(String messageTemplate, PathImpl path) {
    }

    private class ConstraintViolationBuilderImpl implements ConstraintViolationBuilder {
        private final String messageTemplate;
        private final List<Path.Node> nodes = new ArrayList<>();
        // Pending iterable context from a skipped BeanNode - applied to the first added property node
        private boolean pendingInIterable;
        private Integer pendingIndex;
        private Object pendingKey;
        private Class<?> pendingContainerClass;
        private Integer pendingTypeArgIndex;

        ConstraintViolationBuilderImpl(String messageTemplate) {
            this.messageTemplate = messageTemplate;
            // Start with the base path nodes, skipping the implicit BEAN root node
            // and CROSS_PARAMETER nodes (per BV spec: custom violations from cross-parameter
            // validators use addParameterNode to build the path from the method node).
            // When a BeanNode carries iterable context (e.g., from list/map iteration),
            // save it as pending context to apply to the first property node added by the validator.
            for (Path.Node node : basePath) {
                if (node.getKind() == ElementKind.BEAN) {
                    if (node.isInIterable()) {
                        pendingInIterable = true;
                        pendingIndex = node.getIndex();
                        pendingKey = node.getKey();
                        // Extract container class and type arg index from the BeanNode
                        if (node instanceof PathImpl.BeanNodeImpl beanNodeImpl) {
                            pendingContainerClass = beanNodeImpl.getContainerClass();
                            pendingTypeArgIndex = beanNodeImpl.getTypeArgumentIndex();
                        }
                    }
                } else if (node.getKind() != ElementKind.CROSS_PARAMETER) {
                    nodes.add(node);
                }
            }
        }

        /**
         * Creates a PropertyNodeImpl consuming pending iterable context if present.
         * This is used when the basePath's BeanNode had iterable context (e.g., from
         * list/map iteration) that needs to be transferred to the first property node.
         */
        private void clearPendingContext() {
            pendingInIterable = false;
            pendingIndex = null;
            pendingKey = null;
            pendingContainerClass = null;
            pendingTypeArgIndex = null;
        }

        private PathImpl.PropertyNodeImpl createPropertyNodeWithPendingContext(String nodeName) {
            if (pendingInIterable) {
                PathImpl.PropertyNodeImpl node = new PathImpl.PropertyNodeImpl(
                        nodeName, pendingContainerClass, pendingTypeArgIndex);
                if (pendingIndex != null) {
                    node.setIndex(pendingIndex);
                } else if (pendingKey != null) {
                    node.setKey(pendingKey);
                } else {
                    node.setInIterable(true);
                }
                clearPendingContext();
                return node;
            }
            return new PathImpl.PropertyNodeImpl(nodeName);
        }

        private ConstraintValidatorContext recordViolation() {
            customViolations.add(new ViolationEntry(messageTemplate, PathImpl.ofNodes(nodes)));
            return QuarkusConstraintValidatorContext.this;
        }

        private void setLastNodeKey(Object key) {
            if (!nodes.isEmpty()) {
                ((PathImpl.NodeImpl) nodes.get(nodes.size() - 1)).setKey(key);
            }
        }

        private void setLastNodeIndex(Integer index) {
            if (!nodes.isEmpty()) {
                ((PathImpl.NodeImpl) nodes.get(nodes.size() - 1)).setIndex(index);
            }
        }

        @Override
        public NodeBuilderDefinedContext addNode(String name) {
            return new NodeBuilderDefinedContextImpl(name);
        }

        @Override
        public NodeBuilderCustomizableContext addPropertyNode(String name) {
            return new NodeBuilderCustomizableContextImpl(name);
        }

        @Override
        public LeafNodeBuilderCustomizableContext addBeanNode() {
            return new LeafNodeBuilderCustomizableContextImpl();
        }

        @Override
        public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                Class<?> containerType, Integer typeArgumentIndex) {
            return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
        }

        @Override
        public NodeBuilderDefinedContext addParameterNode(int index) {
            // Per BV spec: addParameterNode() can only be called for cross-parameter constraints
            if (constraintKind != null && constraintKind != ElementKind.CROSS_PARAMETER) {
                throw new jakarta.validation.ValidationException(
                        "addParameterNode() can only be called for cross-parameter constraints.");
            }
            // Use the actual parameter name from the provider if available
            String paramName;
            if (parameterNames != null && index >= 0 && index < parameterNames.size()) {
                paramName = parameterNames.get(index);
            } else {
                paramName = "arg" + index;
            }
            nodes.add(new PathImpl.ParameterNodeImpl(paramName, index));
            return new NodeBuilderDefinedContextImpl(null);
        }

        @Override
        public ConstraintValidatorContext addConstraintViolation() {
            return recordViolation();
        }

        private class NodeBuilderDefinedContextImpl implements NodeBuilderDefinedContext {
            NodeBuilderDefinedContextImpl(String name) {
                if (name != null) {
                    nodes.add(createPropertyNodeWithPendingContext(name));
                }
            }

            @Override
            public NodeBuilderCustomizableContext addNode(String name) {
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public NodeBuilderCustomizableContext addPropertyNode(String name) {
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public LeafNodeBuilderCustomizableContext addBeanNode() {
                return new LeafNodeBuilderCustomizableContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                    Class<?> containerType, Integer typeArgumentIndex) {
                return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                return recordViolation();
            }
        }

        private class NodeBuilderCustomizableContextImpl implements NodeBuilderCustomizableContext {
            private final String name;
            private boolean flushed;

            NodeBuilderCustomizableContextImpl(String name) {
                this.name = name;
            }

            private void flushPendingName() {
                if (!flushed) {
                    flushed = true;
                    nodes.add(createPropertyNodeWithPendingContext(name));
                }
            }

            @Override
            public NodeContextBuilder inIterable() {
                flushPendingName();
                // Set isInIterable on the just-flushed node
                if (!nodes.isEmpty()) {
                    ((PathImpl.NodeImpl) nodes.get(nodes.size() - 1)).setInIterable(true);
                }
                return new NodeContextBuilderImpl();
            }

            @Override
            public NodeBuilderCustomizableContext inContainer(Class<?> containerClass, Integer typeArgumentIndex) {
                flushed = true;
                nodes.add(new PathImpl.PropertyNodeImpl(name, containerClass, typeArgumentIndex));
                // Return this (already flushed) so inIterable()/atKey()/atIndex() modify the same node
                return this;
            }

            @Override
            public NodeBuilderCustomizableContext addNode(String name) {
                flushPendingName();
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public NodeBuilderCustomizableContext addPropertyNode(String name) {
                flushPendingName();
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public LeafNodeBuilderCustomizableContext addBeanNode() {
                flushPendingName();
                return new LeafNodeBuilderCustomizableContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                    Class<?> containerType, Integer typeArgumentIndex) {
                flushPendingName();
                return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                flushPendingName();
                return recordViolation();
            }
        }

        private class NodeContextBuilderImpl implements NodeContextBuilder {
            @Override
            public NodeBuilderDefinedContext atKey(Object key) {
                setLastNodeKey(key);
                return new NodeBuilderDefinedContextImpl(null);
            }

            @Override
            public NodeBuilderDefinedContext atIndex(Integer index) {
                setLastNodeIndex(index);
                return new NodeBuilderDefinedContextImpl(null);
            }

            @Override
            public NodeBuilderCustomizableContext addNode(String name) {
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public NodeBuilderCustomizableContext addPropertyNode(String name) {
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public LeafNodeBuilderCustomizableContext addBeanNode() {
                return new LeafNodeBuilderCustomizableContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                    Class<?> containerType, Integer typeArgumentIndex) {
                return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                return recordViolation();
            }
        }

        private class LeafNodeBuilderCustomizableContextImpl implements LeafNodeBuilderCustomizableContext {
            private boolean added;

            @Override
            public LeafNodeContextBuilder inIterable() {
                PathImpl.BeanNodeImpl beanNode = new PathImpl.BeanNodeImpl(null);
                beanNode.setInIterable(true);
                nodes.add(beanNode);
                added = true;
                return new LeafNodeContextBuilderImpl();
            }

            @Override
            public LeafNodeBuilderCustomizableContext inContainer(Class<?> containerClass,
                    Integer typeArgumentIndex) {
                return this;
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                if (!added) {
                    nodes.add(new PathImpl.BeanNodeImpl(null));
                }
                return recordViolation();
            }
        }

        private class LeafNodeContextBuilderImpl implements LeafNodeContextBuilder {
            @Override
            public LeafNodeBuilderDefinedContext atKey(Object key) {
                setLastNodeKey(key);
                return new LeafNodeBuilderDefinedContextImpl();
            }

            @Override
            public LeafNodeBuilderDefinedContext atIndex(Integer index) {
                setLastNodeIndex(index);
                return new LeafNodeBuilderDefinedContextImpl();
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                return recordViolation();
            }
        }

        private class LeafNodeBuilderDefinedContextImpl implements LeafNodeBuilderDefinedContext {
            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                return recordViolation();
            }
        }

        private class ContainerElementNodeBuilderCustomizableContextImpl
                implements ContainerElementNodeBuilderCustomizableContext {
            private final String name;
            private final Class<?> containerType;
            private final Integer typeArgumentIndex;

            ContainerElementNodeBuilderCustomizableContextImpl(String name, Class<?> containerType,
                    Integer typeArgumentIndex) {
                this.name = name;
                this.containerType = containerType;
                this.typeArgumentIndex = typeArgumentIndex;
            }

            private void flushPendingNode() {
                nodes.add(new PathImpl.ContainerElementNodeImpl(name, containerType, typeArgumentIndex));
            }

            @Override
            public ContainerElementNodeContextBuilder inIterable() {
                flushPendingNode();
                return new ContainerElementNodeContextBuilderImpl();
            }

            @Override
            public NodeBuilderCustomizableContext addPropertyNode(String name) {
                flushPendingNode();
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public LeafNodeBuilderCustomizableContext addBeanNode() {
                flushPendingNode();
                return new LeafNodeBuilderCustomizableContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                    Class<?> containerType, Integer typeArgumentIndex) {
                flushPendingNode();
                return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                flushPendingNode();
                return recordViolation();
            }
        }

        private class ContainerElementNodeContextBuilderImpl implements ContainerElementNodeContextBuilder {
            @Override
            public ContainerElementNodeBuilderDefinedContext atKey(Object key) {
                setLastNodeKey(key);
                return new ContainerElementNodeBuilderDefinedContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderDefinedContext atIndex(Integer index) {
                setLastNodeIndex(index);
                return new ContainerElementNodeBuilderDefinedContextImpl();
            }

            @Override
            public NodeBuilderCustomizableContext addPropertyNode(String name) {
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public LeafNodeBuilderCustomizableContext addBeanNode() {
                return new LeafNodeBuilderCustomizableContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                    Class<?> containerType, Integer typeArgumentIndex) {
                return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                return recordViolation();
            }
        }

        private class ContainerElementNodeBuilderDefinedContextImpl
                implements ContainerElementNodeBuilderDefinedContext {
            @Override
            public NodeBuilderCustomizableContext addPropertyNode(String name) {
                return new NodeBuilderCustomizableContextImpl(name);
            }

            @Override
            public LeafNodeBuilderCustomizableContext addBeanNode() {
                return new LeafNodeBuilderCustomizableContextImpl();
            }

            @Override
            public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
                    Class<?> containerType, Integer typeArgumentIndex) {
                return new ContainerElementNodeBuilderCustomizableContextImpl(name, containerType, typeArgumentIndex);
            }

            @Override
            public ConstraintValidatorContext addConstraintViolation() {
                return recordViolation();
            }
        }
    }
}
