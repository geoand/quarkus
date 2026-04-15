package io.quarkus.jackson.deployment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.jackson.spi.PrecomputedJacksonTypeBuildItem;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Tests that Jackson serialization/deserialization works when using
 * precomputed metadata (bypassing reflection-based introspection).
 */
public class JacksonPrecomputedMetadataTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .addBuildChainCustomizer(buildCustomizer())
            .overrideConfigKey("quarkus.jackson.build-time-introspection", "true");

    static Consumer<BuildChainBuilder> buildCustomizer() {
        return new Consumer<BuildChainBuilder>() {
            @Override
            public void accept(BuildChainBuilder builder) {
                builder.addBuildStep(new BuildStep() {
                    @Override
                    public void execute(BuildContext context) {
                        IndexView index = context.consume(CombinedIndexBuildItem.class).getIndex();
                        ClassInfo personInfo = index.getClassByName(Person.class.getName());
                        if (personInfo != null) {
                            context.produce(new PrecomputedJacksonTypeBuildItem(personInfo));
                        }
                        ClassInfo itemInfo = index.getClassByName(Item.class.getName());
                        if (itemInfo != null) {
                            context.produce(new PrecomputedJacksonTypeBuildItem(itemInfo));
                        }
                    }
                }).consumes(CombinedIndexBuildItem.class)
                        .produces(PrecomputedJacksonTypeBuildItem.class)
                        .build();
            }
        };
    }

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializationWithPrecomputedMetadata() throws JsonProcessingException {
        Person person = new Person();
        person.name = "Alice";
        person.age = 30;
        String json = objectMapper.writeValueAsString(person);
        assertThat(json).contains("\"name\"").contains("\"Alice\"");
        assertThat(json).contains("\"age\"").contains("30");
    }

    @Test
    public void testDeserializationWithPrecomputedMetadata() throws JsonProcessingException {
        Person person = objectMapper.readValue("{\"name\":\"Bob\",\"age\":25}", Person.class);
        assertThat(person.name).isEqualTo("Bob");
        assertThat(person.age).isEqualTo(25);
    }

    @Test
    public void testGetterSetterSerialization() throws JsonProcessingException {
        Item item = new Item();
        item.setName("Widget");
        item.setEmail("w@example.com");
        String json = objectMapper.writeValueAsString(item);
        assertThat(json).contains("\"name\"").contains("\"Widget\"");
        assertThat(json).contains("\"email\"").contains("\"w@example.com\"");
    }

    @Test
    public void testGetterSetterDeserialization() throws JsonProcessingException {
        Item item = objectMapper.readValue("{\"name\":\"Gadget\",\"email\":\"g@example.com\"}", Item.class);
        assertThat(item.getName()).isEqualTo("Gadget");
        assertThat(item.getEmail()).isEqualTo("g@example.com");
    }

    @Test
    public void testNonPrecomputedTypeStillWorks() throws JsonProcessingException {
        // OtherBean is NOT nominated for precomputation — it should still work
        // via Jackson's standard reflection-based introspection (fallback)
        OtherBean other = new OtherBean();
        other.value = "hello";
        String json = objectMapper.writeValueAsString(other);
        assertThat(json).contains("\"value\"").contains("\"hello\"");
    }

    public static class Person {
        public String name;
        public int age;
    }

    public static class Item {
        private String name;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class OtherBean {
        public String value;
    }
}
