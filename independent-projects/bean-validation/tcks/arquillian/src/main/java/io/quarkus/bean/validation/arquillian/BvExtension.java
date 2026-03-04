package io.quarkus.bean.validation.arquillian;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.core.spi.LoadableExtension;

public class BvExtension implements LoadableExtension {
    @Override
    public void register(ExtensionBuilder builder) {
        builder.service(DeployableContainer.class, BvDeployableContainer.class);
        builder.service(Protocol.class, BvProtocol.class);
    }
}
