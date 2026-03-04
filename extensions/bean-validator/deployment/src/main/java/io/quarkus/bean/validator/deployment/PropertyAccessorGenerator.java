package io.quarkus.bean.validator.deployment;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

import io.quarkus.bean.validation.BeanPropertyAccessor;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedBeanMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedFieldMetadata;
import io.quarkus.bean.validation.impl.metadata.model.ConstrainedMethodMetadata;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;

/**
 * Generates {@link BeanPropertyAccessor} implementations using Gizmo2.
 * One class is generated per constrained bean, placed in the bean's own package
 * so it can access all constrained members directly. Private members have their
 * modifier removed at build time by the extension's bytecode transformer.
 */
class PropertyAccessorGenerator {

    String generate(ConstrainedBeanMetadata beanMeta, IndexView index, ClassOutput classOutput) {
        String beanClassName = beanMeta.className();
        String accessorClassName = toAccessorClassName(beanClassName);
        String beanPackage = getPackage(beanClassName);

        // Collect accessible fields: fieldName -> (declaringClassName, fieldTypeName)
        // Includes public, protected, and package-private (if same package as bean)
        Map<String, FieldAccessInfo> fieldInfos = new LinkedHashMap<>();
        for (ConstrainedFieldMetadata fieldMeta : beanMeta.fields()) {
            String fieldName = fieldMeta.name();
            if (fieldInfos.containsKey(fieldName)) {
                continue; // skip duplicates (field shadowing)
            }
            if (isFieldAccessible(fieldMeta.declaringClassName(), fieldName, beanPackage, index)) {
                fieldInfos.put(fieldName, new FieldAccessInfo(
                        fieldMeta.declaringClassName(), fieldMeta.fieldTypeName()));
            }
        }

        // Collect accessible getters: methodName -> (declaringClassName, returnTypeName)
        Map<String, MethodAccessInfo> getterInfos = new LinkedHashMap<>();
        for (ConstrainedMethodMetadata methodMeta : beanMeta.methods()) {
            if (!methodMeta.getter()) {
                continue;
            }
            String methodName = methodMeta.name();
            if (getterInfos.containsKey(methodName)) {
                continue;
            }
            if (isMethodAccessible(methodMeta.declaringClassName(), methodName, beanPackage, index)) {
                getterInfos.put(methodName, new MethodAccessInfo(
                        methodMeta.declaringClassName(), methodMeta.returnTypeName()));
            }
        }

        Gizmo gizmo = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);

        gizmo.class_(accessorClassName, cc -> {
            cc.implements_(BeanPropertyAccessor.class);

            // No-arg constructor
            cc.constructor(mc -> {
                mc.public_();
                mc.body(bc -> {
                    bc.invokeSpecial(ConstructorDesc.of(Object.class), cc.this_());
                    bc.return_();
                });
            });

            // getFieldValue(Object bean, String fieldName)
            cc.method("getFieldValue", mc -> {
                mc.public_();
                mc.returning(Object.class);
                ParamVar beanParam = mc.parameter("bean", Object.class);
                ParamVar fieldNameParam = mc.parameter("fieldName", String.class);
                mc.body(bc -> {
                    if (!fieldInfos.isEmpty()) {
                        bc.switch_(fieldNameParam, sc -> {
                            for (Map.Entry<String, FieldAccessInfo> entry : fieldInfos.entrySet()) {
                                String fieldName = entry.getKey();
                                FieldAccessInfo info = entry.getValue();
                                sc.caseOf(Const.of(fieldName), cbc -> {
                                    // Direct field access: ((DeclaringClass) bean).fieldName
                                    ClassDesc declaringClassDesc = ClassDesc.of(info.declaringClassName);
                                    ClassDesc fieldTypeDesc = typeNameToClassDesc(info.fieldTypeName);
                                    Expr castBean = cbc.cast(beanParam, declaringClassDesc);
                                    cbc.return_(castBean.field(
                                            FieldDesc.of(declaringClassDesc, fieldName, fieldTypeDesc)));
                                });
                            }
                        });
                    }
                    // Default: throw ValidationException
                    Expr fieldMsg = bc.invokeVirtual(
                            ClassMethodDesc.of(ClassDesc.of("java.lang.String"), "concat",
                                    String.class, String.class),
                            Const.of("Field not found: "), fieldNameParam);
                    bc.throw_(ClassDesc.of("jakarta.validation.ValidationException"), fieldMsg);
                });
            });

            // getPropertyValue(Object bean, String methodName)
            cc.method("getPropertyValue", mc -> {
                mc.public_();
                mc.returning(Object.class);
                ParamVar beanParam = mc.parameter("bean", Object.class);
                ParamVar methodNameParam = mc.parameter("methodName", String.class);
                mc.body(bc -> {
                    if (!getterInfos.isEmpty()) {
                        bc.switch_(methodNameParam, sc -> {
                            for (Map.Entry<String, MethodAccessInfo> entry : getterInfos.entrySet()) {
                                String methodName = entry.getKey();
                                MethodAccessInfo info = entry.getValue();
                                sc.caseOf(Const.of(methodName), cbc -> {
                                    // Direct method invocation: ((DeclaringClass) bean).methodName()
                                    ClassDesc declaringClassDesc = ClassDesc.of(info.declaringClassName);
                                    ClassDesc returnTypeDesc = typeNameToClassDesc(info.returnTypeName);
                                    Expr castBean = cbc.cast(beanParam, declaringClassDesc);
                                    cbc.return_(cbc.invokeVirtual(
                                            ClassMethodDesc.of(declaringClassDesc, methodName,
                                                    returnTypeDesc),
                                            castBean));
                                });
                            }
                        });
                    }
                    // Default: throw ValidationException
                    Expr methodMsg = bc.invokeVirtual(
                            ClassMethodDesc.of(ClassDesc.of("java.lang.String"), "concat",
                                    String.class, String.class),
                            Const.of("Cannot invoke getter: "), methodNameParam);
                    bc.throw_(ClassDesc.of("jakarta.validation.ValidationException"), methodMsg);
                });
            });
        });

        return accessorClassName;
    }

    static String toAccessorClassName(String beanFqcn) {
        int lastDot = beanFqcn.lastIndexOf('.');
        String pkg = lastDot > 0 ? beanFqcn.substring(0, lastDot) : "";
        String simpleName = beanFqcn.substring(lastDot + 1).replace('$', '_');
        return (pkg.isEmpty() ? "" : pkg + ".") + simpleName + "_BVAccessor";
    }

    /**
     * Checks whether a field is accessible from the bean's package.
     * Private fields are transformed to package-private at build time, so all fields
     * in the same package are accessible.
     */
    private boolean isFieldAccessible(String declaringClassName, String fieldName, String beanPackage, IndexView index) {
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(declaringClassName));
        if (classInfo == null) {
            return false;
        }
        FieldInfo fieldInfo = classInfo.field(fieldName);
        if (fieldInfo == null) {
            return false;
        }
        if (Modifier.isPublic(fieldInfo.flags())) {
            return true;
        }
        // Private fields are transformed to package-private at build time;
        // package-private and protected are accessible if same package
        String declaringPkg = getPackage(declaringClassName);
        return declaringPkg.equals(beanPackage);
    }

    /**
     * Checks whether a no-arg method is accessible from the bean's package.
     * Private methods are transformed to package-private at build time, so all methods
     * in the same package are accessible.
     */
    private boolean isMethodAccessible(String declaringClassName, String methodName, String beanPackage, IndexView index) {
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(declaringClassName));
        if (classInfo == null) {
            return false;
        }
        for (MethodInfo method : classInfo.methods()) {
            if (method.name().equals(methodName) && method.parametersCount() == 0) {
                if (Modifier.isPublic(method.flags())) {
                    return true;
                }
                // Private methods are transformed to package-private at build time;
                // package-private and protected are accessible if same package
                String declaringPkg = getPackage(declaringClassName);
                return declaringPkg.equals(beanPackage);
            }
        }
        return false;
    }

    private static String getPackage(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot > 0 ? fqcn.substring(0, lastDot) : "";
    }

    static ClassDesc typeNameToClassDesc(String typeName) {
        if (typeName == null) {
            return ClassDesc.of("java.lang.Object");
        }
        return switch (typeName) {
            case "int" -> ClassDesc.ofDescriptor("I");
            case "long" -> ClassDesc.ofDescriptor("J");
            case "boolean" -> ClassDesc.ofDescriptor("Z");
            case "double" -> ClassDesc.ofDescriptor("D");
            case "float" -> ClassDesc.ofDescriptor("F");
            case "short" -> ClassDesc.ofDescriptor("S");
            case "byte" -> ClassDesc.ofDescriptor("B");
            case "char" -> ClassDesc.ofDescriptor("C");
            case "void" -> ClassDesc.ofDescriptor("V");
            default -> {
                if (typeName.endsWith("[]")) {
                    yield typeNameToClassDesc(typeName.substring(0, typeName.length() - 2)).arrayType();
                }
                yield ClassDesc.of(typeName);
            }
        };
    }

    private record FieldAccessInfo(String declaringClassName, String fieldTypeName) {
    }

    private record MethodAccessInfo(String declaringClassName, String returnTypeName) {
    }
}
