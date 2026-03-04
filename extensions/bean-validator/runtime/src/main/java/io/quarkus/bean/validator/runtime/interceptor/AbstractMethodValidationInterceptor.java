package io.quarkus.bean.validator.runtime.interceptor;

import java.io.Serializable;
import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;

public abstract class AbstractMethodValidationInterceptor implements Serializable {

    @Inject
    Instance<Validator> validatorInstance;

    protected Object validateMethodInvocation(InvocationContext ctx) throws Exception {
        ExecutableValidator executableValidator = validatorInstance.get().forExecutables();
        Set<ConstraintViolation<Object>> violations = executableValidator.validateParameters(ctx.getTarget(),
                ctx.getMethod(), ctx.getParameters());

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(getMessage(ctx.getMethod(), ctx.getParameters(), violations),
                    violations);
        }

        Object result = ctx.proceed();

        violations = executableValidator.validateReturnValue(ctx.getTarget(), ctx.getMethod(), result);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(getMessage(ctx.getMethod(), ctx.getParameters(), violations),
                    violations);
        }

        return result;
    }

    protected void validateConstructorInvocation(InvocationContext ctx) throws Exception {
        ExecutableValidator executableValidator = validatorInstance.get().forExecutables();
        Set<? extends ConstraintViolation<?>> violations = executableValidator
                .validateConstructorParameters(ctx.getConstructor(), ctx.getParameters());

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(getMessage(ctx.getConstructor(), ctx.getParameters(), violations),
                    violations);
        }

        ctx.proceed();
        Object createdObject = ctx.getTarget();

        violations = executableValidator.validateConstructorReturnValue(ctx.getConstructor(), createdObject);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(getMessage(ctx.getConstructor(), ctx.getParameters(), violations),
                    violations);
        }
    }

    private String getMessage(Member member, Object[] args, Set<? extends ConstraintViolation<?>> violations) {
        StringBuilder message = new StringBuilder();
        message.append(violations.size());
        message.append(" constraint violation(s) occurred during method validation.");
        message.append("\nConstructor or Method: ");
        message.append(member);
        message.append("\nArgument values: ");
        message.append(Arrays.toString(args));
        message.append("\nConstraint violations: ");

        int i = 1;
        for (ConstraintViolation<?> constraintViolation : violations) {
            Path.Node leafNode = getLeafNode(constraintViolation);

            message.append("\n (");
            message.append(i);
            message.append(")");
            message.append(" Kind: ");
            message.append(leafNode.getKind());
            if (leafNode.getKind() == ElementKind.PARAMETER) {
                message.append("\n parameter index: ");
                message.append(leafNode.as(Path.ParameterNode.class).getParameterIndex());
            }
            message.append("\n message: ");
            message.append(constraintViolation.getMessage());
            message.append("\n root bean: ");
            message.append(constraintViolation.getRootBean());
            message.append("\n property path: ");
            message.append(constraintViolation.getPropertyPath());
            message.append("\n constraint: ");
            message.append(constraintViolation.getConstraintDescriptor().getAnnotation());

            i++;
        }

        return message.toString();
    }

    private Path.Node getLeafNode(ConstraintViolation<?> constraintViolation) {
        Iterator<Path.Node> nodes = constraintViolation.getPropertyPath().iterator();
        Path.Node leafNode = null;
        while (nodes.hasNext()) {
            leafNode = nodes.next();
        }
        return leafNode;
    }
}
