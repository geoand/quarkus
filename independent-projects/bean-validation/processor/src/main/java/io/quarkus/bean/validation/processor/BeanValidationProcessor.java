package io.quarkus.bean.validation.processor;

import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.bean.validation.impl.metadata.model.BeanValidationMetadata;

public class BeanValidationProcessor {

    private static final Logger LOG = Logger.getLogger(BeanValidationProcessor.class);

    public BeanValidationMetadata process(IndexView index) {
        LOG.debug("Starting Bean Validation build-time processing");

        ValidatorResolver validatorResolver = new ValidatorResolver(index);
        ConstraintScanner scanner = new ConstraintScanner(index, validatorResolver);
        ScanResult scanResult = scanner.scan();

        LOG.debugf("Scan found %d constrained classes", scanResult.getAllConstrainedClasses().size());
        LOG.debugf("Scan found %d custom constraint annotations", scanResult.getCustomConstraintAnnotations().size());

        MetadataBuilder builder = new MetadataBuilder(scanner);
        BeanValidationMetadata metadata = builder.build(scanResult);

        LOG.debugf("Bean Validation processing complete: %d beans processed", metadata.beans().size());

        return metadata;
    }
}
