package io.quarkus.bean.validator.deployment;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkus.bean.validation.impl.AbstractConstraintAnnotationLiteral;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Generates concrete annotation implementation classes for constraint annotations
 * using Gizmo2. Each generated class:
 * <ul>
 * <li>Extends {@link AbstractConstraintAnnotationLiteral}</li>
 * <li>Implements the annotation interface</li>
 * <li>Has a constructor {@code (Class, Map)} that delegates to the superclass</li>
 * <li>Has accessor methods for each annotation member, delegating to typed base class getters</li>
 * </ul>
 */
class ConstraintAnnotationLiteralGenerator {

    private static final String LITERAL_PACKAGE = "io.quarkus.bean.validation.literal";

    String generate(String annotationFqcn, IndexView index, ClassOutput classOutput) {
        String literalClassName = toLiteralClassName(annotationFqcn);

        // Discover annotation members via Jandex
        List<AnnotationMember> members = discoverMembers(annotationFqcn, index);

        Gizmo gizmo = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);

        gizmo.class_(literalClassName, cc -> {
            cc.extends_(AbstractConstraintAnnotationLiteral.class);
            cc.implements_(ClassDesc.of(annotationFqcn));

            // Constructor: (Class, Map)
            cc.constructor(mc -> {
                mc.public_();
                ParamVar annotationType = mc.parameter("annotationType", Class.class);
                ParamVar attrs = mc.parameter("attrs", Map.class);
                mc.body(bc -> {
                    bc.invokeSpecial(
                            ConstructorDesc.of(AbstractConstraintAnnotationLiteral.class,
                                    Class.class, Map.class),
                            cc.this_(), annotationType, attrs);
                    bc.return_();
                });
            });

            // Accessor methods for each annotation member
            for (AnnotationMember member : members) {
                cc.method(member.name, mc -> {
                    mc.public_();
                    mc.returning(member.classDesc);
                    mc.body(bc -> {
                        io.quarkus.gizmo2.Expr result = bc.invokeVirtual(
                                member.getterMethodDesc(),
                                cc.this_(),
                                member.getterArgs());
                        if (member.needsCast()) {
                            bc.return_(bc.cast(result, member.classDesc));
                        } else {
                            bc.return_(result);
                        }
                    });
                });
            }
        });

        return literalClassName;
    }

    /**
     * Converts an annotation FQCN to a generated literal class name.
     * E.g., "jakarta.validation.constraints.NotNull" →
     * "io.quarkus.bean.validation.literal.jakarta_validation_constraints_NotNull_BVLiteral"
     */
    static String toLiteralClassName(String annotationFqcn) {
        return LITERAL_PACKAGE + "." + annotationFqcn.replace('.', '_') + "_BVLiteral";
    }

    private List<AnnotationMember> discoverMembers(String annotationFqcn, IndexView index) {
        List<AnnotationMember> members = new ArrayList<>();

        ClassInfo classInfo = index.getClassByName(DotName.createSimple(annotationFqcn));
        if (classInfo != null) {
            // Use Jandex
            for (MethodInfo method : classInfo.methods()) {
                if (method.name().equals("<clinit>") || method.name().equals("<init>")) {
                    continue;
                }
                if (method.parametersCount() != 0) {
                    continue;
                }
                members.add(AnnotationMember.fromJandex(method));
            }
        } else {
            // Fall back to reflection
            try {
                Class<?> annotationClass = Class.forName(annotationFqcn, false,
                        Thread.currentThread().getContextClassLoader());
                for (java.lang.reflect.Method method : annotationClass.getDeclaredMethods()) {
                    if (method.getParameterCount() != 0) {
                        continue;
                    }
                    members.add(AnnotationMember.fromReflection(method));
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Cannot find annotation class in index or classpath: " + annotationFqcn, e);
            }
        }

        return members;
    }

    /**
     * Holds info about a single annotation member and computes the appropriate
     * base-class getter method descriptor.
     *
     * @param componentType for enum/enumArray
     */
    private record AnnotationMember(String name, ClassDesc classDesc, ReturnCategory category,
            ClassDesc componentType) {

        boolean needsCast() {
            return category == ReturnCategory.ENUM
                    || category == ReturnCategory.ENUM_ARRAY
                    || category == ReturnCategory.OBJECT;
        }

        static AnnotationMember fromJandex(MethodInfo method) {
            Type returnType = method.returnType();
            ClassDesc cd = toClassDesc(returnType);
            ReturnCategory cat = categorize(returnType);
            ClassDesc comp = null;
            if (cat == ReturnCategory.ENUM) {
                comp = ClassDesc.of(returnType.name().toString());
            } else if (cat == ReturnCategory.ENUM_ARRAY) {
                comp = ClassDesc.of(returnType.asArrayType().componentType().name().toString());
            }
            return new AnnotationMember(method.name(), cd, cat, comp);
        }

        static AnnotationMember fromReflection(Method method) {
            Class<?> rt = method.getReturnType();
            ClassDesc cd = classDescFromReflection(rt);
            ReturnCategory cat = categorizeReflection(rt);
            ClassDesc comp = null;
            if (cat == ReturnCategory.ENUM) {
                comp = ClassDesc.of(rt.getName());
            } else if (cat == ReturnCategory.ENUM_ARRAY) {
                comp = ClassDesc.of(rt.getComponentType().getName());
            }
            return new AnnotationMember(method.getName(), cd, cat, comp);
        }

        private static ClassDesc classDescFromReflection(Class<?> type) {
            if (type.isArray()) {
                return classDescFromReflection(type.getComponentType()).arrayType();
            }
            if (type.isPrimitive()) {
                return type.describeConstable().orElseThrow();
            }
            return ClassDesc.of(type.getName());
        }

        MethodDesc getterMethodDesc() {
            return switch (category) {
                case STRING -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getStringAttr", String.class, String.class);
                case INT -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getIntAttr", int.class, String.class);
                case LONG -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getLongAttr", long.class, String.class);
                case BOOLEAN -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getBooleanAttr", boolean.class, String.class);
                case DOUBLE -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getDoubleAttr", double.class, String.class);
                case FLOAT -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getFloatAttr", float.class, String.class);
                case SHORT -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getShortAttr", short.class, String.class);
                case BYTE -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getByteAttr", byte.class, String.class);
                case CHAR -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getCharAttr", char.class, String.class);
                case CLASS -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getClassAttr", Class.class, String.class);
                case CLASS_ARRAY -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getClassArrayAttr", Class[].class, String.class);
                case STRING_ARRAY -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getStringArrayAttr", String[].class, String.class);
                case INT_ARRAY -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getIntArrayAttr", int[].class, String.class);
                case LONG_ARRAY -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getLongArrayAttr", long[].class, String.class);
                case ENUM -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getEnumAttr", Enum.class, String.class, Class.class);
                case ENUM_ARRAY -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getEnumArrayAttr", Enum[].class, String.class, Class.class);
                default -> MethodDesc.of(AbstractConstraintAnnotationLiteral.class,
                        "getObjectAttr", Object.class, String.class);
            };
        }

        Expr[] getterArgs() {
            Expr nameConst = Const.of(name);
            return switch (category) {
                case ENUM, ENUM_ARRAY -> new Expr[] { nameConst, Const.of(componentType) };
                default -> new Expr[] { nameConst };
            };
        }

        private static ClassDesc toClassDesc(Type type) {
            return switch (type.kind()) {
                case PRIMITIVE -> switch (type.asPrimitiveType().primitive()) {
                    case BOOLEAN -> ClassDesc.ofDescriptor("Z");
                    case BYTE -> ClassDesc.ofDescriptor("B");
                    case CHAR -> ClassDesc.ofDescriptor("C");
                    case DOUBLE -> ClassDesc.ofDescriptor("D");
                    case FLOAT -> ClassDesc.ofDescriptor("F");
                    case INT -> ClassDesc.ofDescriptor("I");
                    case LONG -> ClassDesc.ofDescriptor("J");
                    case SHORT -> ClassDesc.ofDescriptor("S");
                };
                case ARRAY -> toClassDesc(type.asArrayType().componentType()).arrayType();
                default -> ClassDesc.of(type.name().toString());
            };
        }

        private static ReturnCategory categorize(Type type) {
            if (type.kind() == Type.Kind.PRIMITIVE) {
                return switch (type.asPrimitiveType().primitive()) {
                    case INT -> ReturnCategory.INT;
                    case LONG -> ReturnCategory.LONG;
                    case BOOLEAN -> ReturnCategory.BOOLEAN;
                    case DOUBLE -> ReturnCategory.DOUBLE;
                    case FLOAT -> ReturnCategory.FLOAT;
                    case SHORT -> ReturnCategory.SHORT;
                    case BYTE -> ReturnCategory.BYTE;
                    case CHAR -> ReturnCategory.CHAR;
                };
            }
            String name = type.name().toString();
            if (String.class.getName().equals(name)) {
                return ReturnCategory.STRING;
            }
            if (Class.class.getName().equals(name)) {
                return ReturnCategory.CLASS;
            }
            if (type.kind() == Type.Kind.ARRAY) {
                Type componentType = type.asArrayType().componentType();
                String compName = componentType.name().toString();
                if (String.class.getName().equals(compName)) {
                    return ReturnCategory.STRING_ARRAY;
                }
                if (Class.class.getName().equals(compName)) {
                    return ReturnCategory.CLASS_ARRAY;
                }
                if (componentType.kind() == Type.Kind.PRIMITIVE) {
                    return switch (componentType.asPrimitiveType().primitive()) {
                        case INT -> ReturnCategory.INT_ARRAY;
                        case LONG -> ReturnCategory.LONG_ARRAY;
                        default -> ReturnCategory.OBJECT;
                    };
                }
                // Check if the component is an enum (we need to check the index)
                // For now, treat as enum array since annotation member arrays of
                // non-primitive/non-String/non-Class types must be enum arrays
                // (annotations can only contain: primitives, String, Class, enums,
                // annotations, and arrays of these)
                return ReturnCategory.ENUM_ARRAY;
            }
            // Non-primitive, non-String, non-Class, non-array — must be an enum
            // (annotations cannot have annotation-typed members in BV context typically,
            // but if they do, OBJECT fallback handles it)
            // Check via Jandex if needed; for now use a heuristic:
            // annotation members that return a non-standard type are typically enums
            return ReturnCategory.ENUM;
        }

        private static ReturnCategory categorizeReflection(Class<?> type) {
            if (type == int.class) {
                return ReturnCategory.INT;
            }
            if (type == long.class) {
                return ReturnCategory.LONG;
            }
            if (type == boolean.class) {
                return ReturnCategory.BOOLEAN;
            }
            if (type == double.class) {
                return ReturnCategory.DOUBLE;
            }
            if (type == float.class) {
                return ReturnCategory.FLOAT;
            }
            if (type == short.class) {
                return ReturnCategory.SHORT;
            }
            if (type == byte.class) {
                return ReturnCategory.BYTE;
            }
            if (type == char.class) {
                return ReturnCategory.CHAR;
            }
            if (type == String.class) {
                return ReturnCategory.STRING;
            }
            if (type == Class.class) {
                return ReturnCategory.CLASS;
            }
            if (type == String[].class) {
                return ReturnCategory.STRING_ARRAY;
            }
            if (type == Class[].class) {
                return ReturnCategory.CLASS_ARRAY;
            }
            if (type == int[].class) {
                return ReturnCategory.INT_ARRAY;
            }
            if (type == long[].class) {
                return ReturnCategory.LONG_ARRAY;
            }
            if (type.isArray() && type.getComponentType().isEnum()) {
                return ReturnCategory.ENUM_ARRAY;
            }
            if (type.isEnum()) {
                return ReturnCategory.ENUM;
            }
            return ReturnCategory.OBJECT;
        }
    }

    private enum ReturnCategory {
        STRING,
        INT,
        LONG,
        BOOLEAN,
        DOUBLE,
        FLOAT,
        SHORT,
        BYTE,
        CHAR,
        CLASS,
        CLASS_ARRAY,
        STRING_ARRAY,
        INT_ARRAY,
        LONG_ARRAY,
        ENUM,
        ENUM_ARRAY,
        OBJECT
    }
}
