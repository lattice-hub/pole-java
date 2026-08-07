package io.github.latticehub.agent;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class PoleSpringInstaller {
    private static final String COMMON_PREFIX = "io/github/latticehub/adapter/spring/common/";
    private static final Map<Integer, String> INITIALIZERS = Map.of(
            2, "io.github.latticehub.adapter.spring.boot2.PoleSpringBoot2Initializer",
            3, "io.github.latticehub.adapter.spring.boot3.PoleSpringBoot3Initializer",
            4, "io.github.latticehub.adapter.spring.boot4.PoleSpringBoot4Initializer");
    private static final Set<Object> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ClassLoader, WeakReference<AdapterClassLoader>> ADAPTER_LOADERS = new WeakHashMap<>();

    private PoleSpringInstaller() {
    }

    public static void install(Object springApplication) {
        synchronized (INSTALLED) {
            if (!INSTALLED.add(springApplication)) {
                return;
            }
        }
        try {
            Class<?> applicationType = springApplication.getClass();
            ClassLoader applicationLoader = applicationType.getClassLoader();
            int major = bootMajor(applicationType.getPackage().getImplementationVersion());
            String initializerName = INITIALIZERS.get(major);
            if (initializerName == null) {
                throw new IllegalStateException("Pole Java Agent supports Spring Boot 2, 3, and 4 only");
            }
            Class<?> initializerInterface = Class.forName(
                    "org.springframework.context.ApplicationContextInitializer", false, applicationLoader);
            Class<?> initializerType = initializerType(applicationLoader, major, initializerName);
            Object initializer = initializerType.getConstructor().newInstance();
            Object initializers = Array.newInstance(initializerInterface, 1);
            Array.set(initializers, 0, initializer);
            Method addInitializers = applicationType.getMethod("addInitializers", initializers.getClass());
            addInitializers.invoke(springApplication, initializers);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install Spring adapter", exception.getCause());
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install Spring adapter", exception);
        }
    }

    static int bootMajor(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Spring Boot implementation version is unavailable");
        }
        int separator = version.indexOf('.');
        String major = separator < 0 ? version : version.substring(0, separator);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("invalid Spring Boot implementation version: " + version, exception);
        }
    }

    private static AdapterClassLoader adapterClassLoader(ClassLoader parent, int major) throws IOException {
        synchronized (ADAPTER_LOADERS) {
            WeakReference<AdapterClassLoader> reference = ADAPTER_LOADERS.get(parent);
            AdapterClassLoader existing = reference == null ? null : reference.get();
            if (existing != null) {
                return existing;
            }
            Path adapterJar = adapterJar(major);
            AdapterClassLoader created = new AdapterClassLoader(adapterJar.toUri().toURL(), parent);
            ADAPTER_LOADERS.put(parent, new WeakReference<>(created));
            return created;
        }
    }

    private static Path adapterJar(int major) throws IOException {
        String versionPrefix = "io/github/latticehub/adapter/spring/boot" + major + "/";
        Map<String, byte[]> classes = adapterClasses(COMMON_PREFIX, versionPrefix);
        Path jar = Files.createTempFile("pole-spring-boot-" + major + "-", ".jar");
        jar.toFile().deleteOnExit();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey().replace('.', '/') + ".class"));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }

    private static Class<?> initializerType(ClassLoader classLoader, int major, String initializerName)
            throws ClassNotFoundException, IOException {
        return Class.forName(initializerName, true, adapterClassLoader(classLoader, major));
    }

    private static Map<String, byte[]> adapterClasses(String... prefixes) throws IOException {
        URI source = sourceLocation();
        Path path = Path.of(source);
        LinkedHashMap<String, byte[]> classes = new LinkedHashMap<>();
        if (Files.isDirectory(path)) {
            for (String prefix : prefixes) {
                Path root = path.resolve(prefix);
                if (!Files.exists(root)) {
                    continue;
                }
                try (var stream = Files.walk(root)) {
                    stream.filter(file -> file.toString().endsWith(".class")).forEach(file -> {
                        try {
                            String name = path.relativize(file).toString().replace(path.getFileSystem().getSeparator(), "/");
                            classes.put(className(name), Files.readAllBytes(file));
                        } catch (IOException exception) {
                            throw new AdapterReadException(exception);
                        }
                    });
                } catch (AdapterReadException exception) {
                    throw exception.ioException;
                }
            }
        } else {
            try (JarFile jar = new JarFile(path.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class") && matches(entry.getName(), prefixes)) {
                        classes.put(className(entry.getName()), jar.getInputStream(entry).readAllBytes());
                    }
                }
            }
        }
        if (classes.isEmpty()) {
            throw new IOException("Pole adapter classes are missing from Agent JAR");
        }
        return classes;
    }

    private static URI sourceLocation() {
        try {
            return PoleSpringInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot locate Pole Java Agent", exception);
        }
    }

    private static boolean matches(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String className(String entryName) {
        return entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
    }

    private static final class AdapterReadException extends RuntimeException {
        private final IOException ioException;

        private AdapterReadException(IOException ioException) {
            this.ioException = ioException;
        }
    }

    private static final class AdapterClassLoader extends URLClassLoader {
        private static final String ADAPTER_PACKAGE = "io.github.latticehub.adapter.spring.";

        private AdapterClassLoader(URL adapterJar, ClassLoader parent) {
            super(new URL[]{adapterJar}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith(ADAPTER_PACKAGE)) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
