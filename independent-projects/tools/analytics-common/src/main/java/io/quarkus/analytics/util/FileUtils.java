package io.quarkus.analytics.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.analytics.dto.config.LocalConfig;
import io.quarkus.analytics.dto.config.RemoteConfig;
import io.quarkus.bootstrap.json.JsonObject;
import io.quarkus.bootstrap.json.JsonReader;
import io.quarkus.devtools.messagewriter.MessageWriter;

public class FileUtils {

    /**
     * Creates the file for the given path and the folder that contains it.
     * Does nothing if it any of those already exist.
     *
     * @param path the file to create
     *
     * @throws IOException if the file operation fails
     */
    public static void createFileAndParent(Path path) throws IOException {
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
    }

    /**
     * Writes a String to file
     *
     * @param content
     * @param path
     * @throws IOException
     */
    public static void append(String content, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.append(content);
        }
    }

    /**
     * Writes an object, as JSON to file
     *
     * @param content
     * @param path
     * @throws IOException
     */
    public static void write(JsonSerializable content, Path path) throws IOException {
        createFileAndParent(path);
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(content.toJson());
        }
    }

    /**
     * Writes an object, as JSON to file. Deletes previous file if it exists, before writing the new one.
     *
     * @param content
     * @param path
     * @throws IOException
     */
    public static void overwrite(JsonSerializable content, Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
        createFileAndParent(path);
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(content.toJson());
        }
    }

    public static <T> Optional<T> read(Class<T> clazz, Path path, MessageWriter log) throws IOException {
        try {
            String jsonContent = Files.readString(path);
            JsonObject jsonObject = JsonReader.of(jsonContent).read();
            return Optional.of(deserialize(clazz, jsonObject));
        } catch (Exception e) {
            log.warn("[Quarkus build analytics] Could not read {}", path.toString(), e);
            return Optional.empty();
        } catch (Throwable t) {
            log.error("[Quarkus build analytics] Unexpected error reading class " + t.getClass().getName() +
                    " from path: " + path.toString() +
                    ". Got message: " + t.getMessage() +
                    ". Attempting to continue...");
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(Class<T> clazz, JsonObject jsonObject) {
        if (clazz == LocalConfig.class) {
            return (T) LocalConfig.fromJson(jsonObject);
        } else if (clazz == RemoteConfig.class) {
            return (T) RemoteConfig.fromJson(jsonObject);
        }
        throw new IllegalArgumentException("Unsupported class: " + clazz.getName());
    }

    /**
     * Interface for objects that can be serialized to JSON.
     */
    public interface JsonSerializable {
        String toJson();
    }
}
