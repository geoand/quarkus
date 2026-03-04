package io.quarkus.bean.validation.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import jakarta.validation.ElementKind;
import jakarta.validation.Path;

/**
 * Implementation of {@link jakarta.validation.Path} representing the navigation path
 * from a root object to the validated element.
 * <p>
 * A path is composed of {@link Node} objects, each representing one step in the
 * navigation from the root bean to the constrained element. This implementation
 * supports all nine node types defined in the Bean Validation specification.
 */
public class PathImpl implements Path {

    private final List<Node> nodes;

    private PathImpl(List<Node> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    public static PathImpl createRootPath() {
        return new PathImpl(Collections.emptyList());
    }

    /**
     * Creates a path with a single {@link BeanNodeImpl} having a {@code null} name,
     * representing the root bean itself.
     */
    public static PathImpl createPathForBean() {
        List<Node> nodes = new ArrayList<>(1);
        nodes.add(new BeanNodeImpl(null));
        return new PathImpl(nodes);
    }

    /**
     * Creates a path representing a property on the root bean: a {@link BeanNodeImpl}
     * with {@code null} name followed by a {@link PropertyNodeImpl} with the given name.
     *
     * @param propertyName the property name
     */
    public static PathImpl createPathForProperty(String propertyName) {
        List<Node> nodes = new ArrayList<>(2);
        nodes.add(new BeanNodeImpl(null));
        nodes.add(new PropertyNodeImpl(propertyName));
        return new PathImpl(nodes);
    }

    public static PathImpl ofNodes(List<Node> nodes) {
        return new PathImpl(nodes);
    }

    public PathImpl append(Node node) {
        List<Node> newNodes = new ArrayList<>(nodes.size() + 1);
        newNodes.addAll(nodes);
        newNodes.add(node);
        return new PathImpl(newNodes);
    }

    @Override
    public Iterator<Node> iterator() {
        return nodes.iterator();
    }

    /**
     * Returns the string representation of this path following the Bean Validation
     * specification rules:
     * <ul>
     * <li>Bean nodes with {@code null} name contribute nothing.</li>
     * <li>Property and other named nodes contribute their name.</li>
     * <li>Multiple node names are separated by {@code "."}.</li>
     * <li>If a node is in an iterable and has an index: {@code [index]} is appended.</li>
     * <li>If a node is in an iterable and has a key: {@code [key]} is appended.</li>
     * <li>If a node is in an iterable with neither index nor key: {@code []} is appended.</li>
     * </ul>
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Node node : nodes) {
            String name = node.getName();
            if (name != null) {
                if (!first) {
                    sb.append('.');
                }
                sb.append(name);
                first = false;
            }
            if (node.isInIterable()) {
                if (node.getIndex() != null) {
                    sb.append('[').append(node.getIndex()).append(']');
                } else if (node.getKey() != null) {
                    sb.append('[').append(node.getKey()).append(']');
                } else {
                    sb.append("[]");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    // ---- Node implementations ----

    public static abstract class NodeImpl implements Path.Node {

        private final String name;
        private final ElementKind kind;
        private Integer index;
        private Object key;
        private boolean inIterable;

        NodeImpl(String name, ElementKind kind) {
            this.name = name;
            this.kind = kind;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ElementKind getKind() {
            return kind;
        }

        @Override
        public Integer getIndex() {
            return index;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public boolean isInIterable() {
            return inIterable;
        }

        @Override
        public <T extends Node> T as(Class<T> nodeType) {
            if (!nodeType.isAssignableFrom(getClass())) {
                throw new ClassCastException("Cannot cast node of kind " + kind + " to " + nodeType.getName());
            }
            return nodeType.cast(this);
        }

        public void setIndex(Integer index) {
            this.index = index;
            this.inIterable = true;
        }

        public void setKey(Object key) {
            this.key = key;
            this.inIterable = true;
        }

        public void setInIterable(boolean inIterable) {
            this.inIterable = inIterable;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NodeImpl node = (NodeImpl) o;
            return inIterable == node.inIterable
                    && kind == node.kind
                    && java.util.Objects.equals(name, node.name)
                    && java.util.Objects.equals(index, node.index)
                    && java.util.Objects.equals(key, node.key);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, kind, index, key, inIterable);
        }
    }

    /**
     * Abstract base class for nodes that carry container class and type argument index
     * ({@link Path.BeanNode}, {@link Path.PropertyNode}, {@link Path.ContainerElementNode}).
     */
    static abstract class ContainerAwareNodeImpl extends NodeImpl {

        private final Class<?> containerClass;
        private final Integer typeArgumentIndex;

        ContainerAwareNodeImpl(String name, ElementKind kind, Class<?> containerClass, Integer typeArgumentIndex) {
            super(name, kind);
            this.containerClass = containerClass;
            this.typeArgumentIndex = typeArgumentIndex;
        }

        public Class<?> getContainerClass() {
            return containerClass;
        }

        public Integer getTypeArgumentIndex() {
            return typeArgumentIndex;
        }
    }

    public static class BeanNodeImpl extends ContainerAwareNodeImpl implements Path.BeanNode {

        public BeanNodeImpl(String name) {
            this(name, null, null);
        }

        public BeanNodeImpl(String name, Class<?> containerClass, Integer typeArgumentIndex) {
            super(name, ElementKind.BEAN, containerClass, typeArgumentIndex);
        }
    }

    public static class PropertyNodeImpl extends ContainerAwareNodeImpl implements Path.PropertyNode {

        public PropertyNodeImpl(String name) {
            this(name, null, null);
        }

        public PropertyNodeImpl(String name, Class<?> containerClass, Integer typeArgumentIndex) {
            super(name, ElementKind.PROPERTY, containerClass, typeArgumentIndex);
        }
    }

    static abstract class ExecutableNodeImpl extends NodeImpl {

        private final List<Class<?>> parameterTypes;

        ExecutableNodeImpl(String name, ElementKind kind, List<Class<?>> parameterTypes) {
            super(name, kind);
            this.parameterTypes = parameterTypes == null
                    ? Collections.emptyList()
                    : List.copyOf(parameterTypes);
        }

        public List<Class<?>> getParameterTypes() {
            return parameterTypes;
        }
    }

    public static class MethodNodeImpl extends ExecutableNodeImpl implements Path.MethodNode {

        public MethodNodeImpl(String name, List<Class<?>> parameterTypes) {
            super(name, ElementKind.METHOD, parameterTypes);
        }
    }

    public static class ConstructorNodeImpl extends ExecutableNodeImpl implements Path.ConstructorNode {

        public ConstructorNodeImpl(String name, List<Class<?>> parameterTypes) {
            super(name, ElementKind.CONSTRUCTOR, parameterTypes);
        }
    }

    public static class ParameterNodeImpl extends NodeImpl implements Path.ParameterNode {

        private final int parameterIndex;

        public ParameterNodeImpl(String name, int parameterIndex) {
            super(name, ElementKind.PARAMETER);
            this.parameterIndex = parameterIndex;
        }

        @Override
        public int getParameterIndex() {
            return parameterIndex;
        }
    }

    public static class ReturnValueNodeImpl extends NodeImpl implements Path.ReturnValueNode {

        public ReturnValueNodeImpl() {
            super("<return value>", ElementKind.RETURN_VALUE);
        }
    }

    public static class CrossParameterNodeImpl extends NodeImpl implements Path.CrossParameterNode {

        public CrossParameterNodeImpl() {
            super("<cross-parameter>", ElementKind.CROSS_PARAMETER);
        }
    }

    public static class ContainerElementNodeImpl extends ContainerAwareNodeImpl implements Path.ContainerElementNode {

        public ContainerElementNodeImpl(String name, Class<?> containerClass, Integer typeArgumentIndex) {
            super(name, ElementKind.CONTAINER_ELEMENT, containerClass, typeArgumentIndex);
        }
    }
}
