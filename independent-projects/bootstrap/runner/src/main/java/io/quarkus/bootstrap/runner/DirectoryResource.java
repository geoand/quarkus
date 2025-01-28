package io.quarkus.bootstrap.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Objects;

/**
 * A directory resource
 */
public class DirectoryResource implements ClassLoadingResource {

    private volatile ProtectionDomain protectionDomain;
    private final ManifestInfo manifestInfo;

    private final Path directoryPath;

    public DirectoryResource(ManifestInfo manifestInfo, Path directoryPath) {
        this.manifestInfo = manifestInfo;
        this.directoryPath = directoryPath;
    }

    @Override
    public void init() {
        final URL url;
        try {
            String path = directoryPath.toAbsolutePath().toString();
            if (!path.startsWith("/")) {
                path = '/' + path;
            }
            URI uri = new URI("file", null, path, null);
            url = uri.toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException("Unable to create protection domain for " + directoryPath, e);
        }
        this.protectionDomain = new ProtectionDomain(new CodeSource(url, (Certificate[]) null), null);
    }

    @Override
    public byte[] getResourceData(String resource) {
        try {
            return Files.readAllBytes(resourceNameToPath(resource));
        } catch (NoSuchFileException ignored) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public URL getResourceURL(String resource) {
        try {
            return resourceNameToPath(resource).toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private Path resourceNameToPath(String resource) {
        String[] parts = resource.split("/");
        Path effectivePath = directoryPath;
        for (String part : parts) {
            effectivePath = effectivePath.resolve(part);
        }
        return effectivePath;
    }

    @Override
    public ManifestInfo getManifestInfo() {
        return manifestInfo;
    }

    @Override
    public ProtectionDomain getProtectionDomain() {
        return protectionDomain;
    }

    @Override
    public void close() {

    }

    @Override
    public void resetInternalCaches() {
        //Currently same implementations as #close
        close();
    }

    @Override
    public String toString() {
        return "DirectoryResource{" +
                directoryPath.getFileName() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DirectoryResource that = (DirectoryResource) o;
        return directoryPath.equals(that.directoryPath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(directoryPath);
    }
}
