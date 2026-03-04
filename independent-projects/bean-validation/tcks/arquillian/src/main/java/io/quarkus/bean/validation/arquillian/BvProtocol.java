package io.quarkus.bean.validation.arquillian;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.test.impl.client.protocol.local.LocalDeploymentPackager;
import org.jboss.arquillian.container.test.impl.execution.event.LocalExecutionEvent;
import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.container.test.spi.command.CommandCallback;
import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;

import io.quarkus.bean.validation.arquillian.utils.ClassLoading;

public class BvProtocol implements Protocol<BvProtocolConfiguration> {
    @Inject
    Instance<Injector> injector;

    @Override
    public Class<BvProtocolConfiguration> getProtocolConfigurationClass() {
        return BvProtocolConfiguration.class;
    }

    @Override
    public ProtocolDescription getDescription() {
        return new ProtocolDescription("BeanValidation");
    }

    @Override
    public DeploymentPackager getPackager() {
        return new LocalDeploymentPackager();
    }

    @Override
    public ContainerMethodExecutor getExecutor(BvProtocolConfiguration protocolConfiguration,
            ProtocolMetaData metaData, CommandCallback callback) {
        return injector.get().inject(new BvMethodExecutor());
    }

    static class BvMethodExecutor implements ContainerMethodExecutor {
        @Inject
        Event<LocalExecutionEvent> event;

        @Inject
        Instance<TestResult> testResult;

        @Inject
        Instance<DeploymentClassLoader> deploymentClassLoader;

        @Override
        public TestResult invoke(TestMethodExecutor testMethodExecutor) {
            event.fire(new LocalExecutionEvent(new TestMethodExecutor() {
                @Override
                public String getMethodName() {
                    return testMethodExecutor.getMethod().getName();
                }

                @Override
                public Method getMethod() {
                    return testMethodExecutor.getMethod();
                }

                @Override
                public Object getInstance() {
                    return BvDeployableContainer.testInstance;
                }

                @Override
                public void invoke(Object... ignored) throws Throwable {
                    ClassLoader old = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(deploymentClassLoader.get());

                        Object actualTestInstance = BvDeployableContainer.testInstance;

                        Method actualMethod;
                        try {
                            actualMethod = actualTestInstance.getClass().getMethod(
                                    getMethod().getName(),
                                    ClassLoading.convertToTCCL(getMethod().getParameterTypes()));
                        } catch (NoSuchMethodException e) {
                            actualMethod = actualTestInstance.getClass().getDeclaredMethod(
                                    getMethod().getName(),
                                    ClassLoading.convertToTCCL(getMethod().getParameterTypes()));
                            actualMethod.setAccessible(true);
                        }

                        try {
                            actualMethod.invoke(actualTestInstance);
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            throw ClassLoading.cloneExceptionIntoSystemCL(cause);
                        }
                    } finally {
                        Thread.currentThread().setContextClassLoader(old);
                    }
                }
            }));

            return testResult.get();
        }
    }
}
