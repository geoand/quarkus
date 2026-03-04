package io.quarkus.bean.validation.arquillian;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

public class BvContainerConfiguration implements ContainerConfiguration {
    @Override
    public void validate() throws ConfigurationException {
    }
}
