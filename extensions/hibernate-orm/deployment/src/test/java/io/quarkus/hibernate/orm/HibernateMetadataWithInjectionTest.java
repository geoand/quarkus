package io.quarkus.hibernate.orm;

import static org.assertj.core.api.Assertions.*;

import javax.inject.*;
import javax.persistence.*;

import org.jboss.shrinkwrap.api.*;
import org.jboss.shrinkwrap.api.spec.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

import io.quarkus.hibernate.orm.enhancer.*;
import io.quarkus.test.*;

public class HibernateMetadataWithInjectionTest {

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(Address.class)
                    .addClass(MyEntity.class)
                    .addAsResource("application.properties"));

    @Inject
    HibernateMetadata hibernateMetadata;

    @Inject
    EntityManager em;

    @Test
    public void testExpectedEntityNames() {
        assertThat(hibernateMetadata).isNotNull().satisfies(h -> {
            assertThat(h.getDefaultPersistenceUnitMetadata()).hasValueSatisfying(pu -> {
                assertThat(pu.getEntityClassNames()).containsOnly(Address.class.getName(), MyEntity.class.getName());
            });
        });
    }

    @Test
    public void testExpectedEnties() {
        assertThat(hibernateMetadata).isNotNull().satisfies(h -> {
            assertThat(h.getDefaultPersistenceUnitMetadata()).hasValueSatisfying(pu -> {
                assertThat(pu.resolveEntityClasses()).containsOnly(Address.class, MyEntity.class);
            });
        });
    }
}
