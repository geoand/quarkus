package io.quarkus.bean.validator.deployment;

import java.lang.constant.ClassDesc;
import java.util.Collection;

import io.quarkus.bean.validation.ValidatorInstantiator;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;

/**
 * Generates a {@link ValidatorInstantiator} implementation using Gizmo2.
 * The generated class has a switch on the validator class name and creates
 * instances with {@code new} instead of reflection.
 */
class ValidatorInstantiatorGenerator {

    static final String GENERATED_CLASS_NAME = "io.quarkus.bean.validation.generated.GeneratedValidatorInstantiator";

    void generate(Collection<String> validatorClassNames, ClassOutput classOutput) {
        Gizmo gizmo = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);

        gizmo.class_(GENERATED_CLASS_NAME, cc -> {
            cc.implements_(ValidatorInstantiator.class);

            // No-arg constructor
            cc.constructor(mc -> {
                mc.public_();
                mc.body(bc -> {
                    bc.invokeSpecial(ConstructorDesc.of(Object.class), cc.this_());
                    bc.return_();
                });
            });

            // getInstance(Class key) - switch on key.getName()
            cc.method("getInstance", mc -> {
                mc.public_();
                mc.returning(ClassDesc.of("jakarta.validation.ConstraintValidator"));
                ParamVar keyParam = mc.parameter("key", Class.class);
                mc.body(bc -> {
                    // String name = key.getName();
                    Expr name = bc.invokeVirtual(
                            ClassMethodDesc.of(ClassDesc.of("java.lang.Class"), "getName",
                                    String.class),
                            keyParam);

                    bc.switch_(name, sc -> {
                        for (String validatorClassName : validatorClassNames) {
                            sc.caseOf(Const.of(validatorClassName), cbc -> {
                                ClassDesc validatorDesc = ClassDesc.of(validatorClassName);
                                cbc.return_(cbc.new_(ConstructorDesc.of(validatorDesc)));
                            });
                        }
                    });
                    // Default: return null (unknown validator, caller falls back)
                    bc.return_(Const.ofNull(ClassDesc.of("jakarta.validation.ConstraintValidator")));
                });
            });
        });
    }
}
