package io.quarkus.bean.validator.deployment;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;
import org.objectweb.asm.Opcodes;

import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.bean.validator.runtime.interceptor.MethodValidated;
import io.quarkus.bean.validator.runtime.jaxrs.JaxrsEndPointValidated;
import io.quarkus.deployment.index.IndexingUtil;

public class MethodValidatedAnnotationsTransformer implements AnnotationsTransformer {

    private static final Logger LOGGER = Logger.getLogger(MethodValidatedAnnotationsTransformer.class);

    private final Set<DotName> consideredAnnotations;
    private final Map<DotName, Set<SimpleMethodSignatureKey>> jaxRsMethods;

    MethodValidatedAnnotationsTransformer(Set<DotName> consideredAnnotations,
            Map<DotName, Set<SimpleMethodSignatureKey>> jaxRsMethods) {
        this.consideredAnnotations = consideredAnnotations;
        this.jaxRsMethods = jaxRsMethods;
    }

    @Override
    public boolean appliesTo(Kind kind) {
        return Kind.METHOD == kind;
    }

    @Override
    public void transform(TransformationContext transformationContext) {
        MethodInfo method = transformationContext.getTarget().asMethod();

        if (requiresValidation(method)) {
            if (Modifier.isStatic(method.flags())) {
                LOGGER.warnf(
                        "Bean Validator does not support constraints on static methods. Constraints on %s are ignored.",
                        method.declaringClass().name().toString() + "#" + method);
                return;
            }

            if (isSynthetic(method.flags())) {
                return;
            }

            if (isJaxrsMethod(method)) {
                transformationContext.transform().add(DotName.createSimple(JaxrsEndPointValidated.class.getName())).done();
            } else {
                transformationContext.transform().add(DotName.createSimple(MethodValidated.class.getName())).done();
            }
        }
    }

    private boolean requiresValidation(MethodInfo method) {
        for (DotName consideredAnnotation : consideredAnnotations) {
            if (method.hasAnnotation(consideredAnnotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSynthetic(int mod) {
        return (mod & Opcodes.ACC_SYNTHETIC) != 0;
    }

    private boolean isJaxrsMethod(MethodInfo method) {
        if (jaxRsMethods.isEmpty()) {
            return false;
        }

        ClassInfo clazz = method.declaringClass();
        SimpleMethodSignatureKey signatureKey = new SimpleMethodSignatureKey(method);

        if (isJaxrsMethod(signatureKey, clazz.name())) {
            return true;
        }

        // check interfaces
        for (DotName iface : clazz.interfaceNames()) {
            if (isJaxrsMethod(signatureKey, iface)) {
                return true;
            }
        }

        // check direct superclass
        DotName superClass = clazz.superName();
        if (!superClass.equals(IndexingUtil.OBJECT)) {
            if (isJaxrsMethod(signatureKey, superClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJaxrsMethod(SimpleMethodSignatureKey signatureKey, DotName dotName) {
        Set<SimpleMethodSignatureKey> signatureKeys = jaxRsMethods.get(dotName);
        return signatureKeys != null && signatureKeys.contains(signatureKey);
    }
}
