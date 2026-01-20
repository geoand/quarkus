package io.quarkus.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;
import java.util.ArrayList;
import java.util.List;

public final class MemoryProtocolProvider extends URLStreamHandlerProvider {

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("quarkus-mem".equals(protocol)) {
            return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                    // TODO: implement
                    if (u.toExternalForm().endsWith("/META-INF/microprofile-config.properties")) {
                        System.out.println("hit");
                        return new URLConnection(u) {
                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(new byte[0]);
                            }

                            @Override
                            public void connect() throws IOException {

                            }
                        };
                    }
                    return null;
                }
            };
        }
        return null;
    }

    @SuppressWarnings("unused") // used in generated io.quarkus.runner.ApplicationImpl
    public static void tryInjectIntoCL() {
        try {
            ClassLoader appLoader = ClassLoader.getSystemClassLoader();

            // 1. Fetch the 'ucp' (URLClassPath) from BuiltinClassLoader
            Field ucpField = findField(appLoader.getClass(), "ucp");
            ucpField.setAccessible(true);
            Object ucp = ucpField.get(appLoader);

            synchronized (ucp) {
                Class<?> ucpClass = ucp.getClass();

                // 2. Access 'path' and 'loaders'
                Field pathField = ucpClass.getDeclaredField("path");
                pathField.setAccessible(true);
                ArrayList<URL> path = (ArrayList<URL>) pathField.get(ucp);

                Field loadersField = ucpClass.getDeclaredField("loaders");
                loadersField.setAccessible(true);
                List<Object> loaders = (List<Object>) loadersField.get(ucp);

                // 3. Find the inner class 'Loader' (it handles generic URLs)
                // It is usually a private static inner class or package-private class
                Class<?> loaderClass = null;
                for (Class<?> c : ucpClass.getDeclaredClasses()) {
                    if (c.getSimpleName().equals("Loader")) {
                        loaderClass = c;
                        break;
                    }
                }

                if (loaderClass == null) {
                    throw new IllegalStateException("Could not find URLClassPath$Loader class");
                }

                // register with URL
                URL.setURLStreamHandlerFactory(new MemoryProtocolProvider());

                URL memoryUrl = new URL("quarkus-mem://services/");

                // 4. Create a new Loader instance for your Memory URL
                // Constructor usually takes (URL)
                Constructor<?> loaderCtor = loaderClass.getDeclaredConstructor(URL.class);
                loaderCtor.setAccessible(true);
                Object memoryLoader = loaderCtor.newInstance(memoryUrl);

                // 5. INJECT without clearing
                // We insert at 0. Existing loaders are shifted but their state (ZipFiles) remains open.
                path.add(0, memoryUrl);
                loaders.add(0, memoryLoader);

                // Note: We do NOT clear lmap (lookup map).
                // It will lazily update as new lookups happen.
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
