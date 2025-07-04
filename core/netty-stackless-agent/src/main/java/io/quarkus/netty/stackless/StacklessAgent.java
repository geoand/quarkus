package io.quarkus.netty.stackless;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import io.github.dmlloyd.classfile.ClassFile;
import io.github.dmlloyd.classfile.ClassModel;
import io.github.dmlloyd.classfile.MethodModel;
import io.github.dmlloyd.classfile.MethodTransform;

public class StacklessAgent {

    private static final String TARGET_CLASS_NAME = "io.netty.channel.StacklessClosedChannelException";
    private static final String TARGET_CLASS_BINARY_NAME = TARGET_CLASS_NAME.replace('.', '/');
    private static final ClassDesc TARGET_CLASS_DESC = ClassDesc.of(TARGET_CLASS_NAME);

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {

                if (className.equals(TARGET_CLASS_BINARY_NAME)) {
                    ClassFile cc = ClassFile.of();
                    ClassModel classModel = cc.parse(classfileBuffer);

                    System.out.println("Transforming StacklessClosedChannelException");

                    return cc.transformClass(classModel, (classBuilder, element) -> {
                        if (element instanceof MethodModel mm) {
                            String methodName = mm.methodName().stringValue();
                            if (methodName.equals("fillInStackTrace")) {
                                // drop this method so we can then inherit the default which is to fill the stacktrace
                            } else if (methodName.equals("newInstance")) {
                                // simply call the constructor so we can avoid dropping the stacktrace that is done
                                // by the default implementation
                                classBuilder.transformMethod(mm,
                                        MethodTransform.transformingCode((codeBuilder, codeElement) -> {
                                            codeBuilder.new_(TARGET_CLASS_DESC);
                                            codeBuilder.dup();
                                            codeBuilder.invokespecial(TARGET_CLASS_DESC, "<init>",
                                                    MethodTypeDesc.ofDescriptor("()V"));
                                            codeBuilder.areturn();
                                        }));
                            } else {
                                // pass through
                                classBuilder.with(element);
                            }
                        } else {
                            // pass through
                            classBuilder.with(element);
                        }
                    });
                }
                return null;
            }
        });
    }
}
