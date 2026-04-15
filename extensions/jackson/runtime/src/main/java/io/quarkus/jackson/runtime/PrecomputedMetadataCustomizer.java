package io.quarkus.jackson.runtime;

import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkus.jackson.ObjectMapperCustomizer;

/**
 * Metadata is stored via static methods called by the recorder at static-init time.
 */
public class PrecomputedMetadataCustomizer implements ObjectMapperCustomizer {

    private static final Logger LOG = Logger.getLogger(PrecomputedClassIntrospector.class);

    private static volatile PrecomputedClassIntrospector introspector;

    /**
     * It is very unfortunate that we have to do this like instead of creating a Synthetic Bean, however
     * that can't be done at the moment as it creates build cycles with Quarkus REST.
     * To be able to use a Synthetic Bean, we would need to untangle the {@code setupEndpoints} method of the Quarkus REST
     * extension from {@code BeanContainerBuildItem}
     */
    public static void setMetadata(Map<String, PrecomputedClassMetadata> classToMetadata) {
        introspector = new PrecomputedClassIntrospector(classToMetadata);
    }

    @Override
    public void customize(ObjectMapper objectMapper) {
        if (introspector != null) {
            objectMapper.registerModule(new SimpleModule("QuarkusPrecomputedMetadata") {
                @Override
                public void setupModule(SetupContext context) {
                    super.setupModule(context);
                    context.setClassIntrospector(introspector);
                }
            });
        } else {
            LOG.warn(
                    "Introspector is null. This is an integration issue that should be reported to the Quarkus team, but will otherwise not affect the the functionality of the application");
        }
    }

    @Override
    public int priority() {
        // Apply before other customizers so the introspector is set first
        return QUARKUS_CUSTOMIZER_PRIORITY - 1;
    }
}
