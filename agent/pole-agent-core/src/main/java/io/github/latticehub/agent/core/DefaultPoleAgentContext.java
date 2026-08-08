package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentContext;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class DefaultPoleAgentContext implements PoleAgentContext {
    private final Instrumentation instrumentation;
    private final URI distributionLocation;
    private final Map<ClassLoader, Map<String, WeakReference<PayloadClassLoader>>> payloadLoaders = new WeakHashMap<>();

    DefaultPoleAgentContext(Instrumentation instrumentation) {
        this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
        this.distributionLocation = sourceLocation();
    }

    @Override
    public Instrumentation instrumentation() {
        return instrumentation;
    }

    @Override
    public Class<?> loadIsolatedClass(
            String pluginId,
            String payloadId,
            ClassLoader applicationClassLoader,
            String className,
            Collection<String> childFirstPackages) throws IOException, ClassNotFoundException {
        String loaderId = requireIdentifier(pluginId, "pluginId") + ':' + requireIdentifier(payloadId, "payloadId");
        List<String> packages = childFirstPackages.stream()
                .map(DefaultPoleAgentContext::normalizePackage)
                .distinct()
                .toList();
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("childFirstPackages must not be empty");
        }
        return Class.forName(className, true, payloadClassLoader(applicationClassLoader, loaderId, packages));
    }

    private PayloadClassLoader payloadClassLoader(ClassLoader parent, String loaderId, List<String> packages)
            throws IOException {
        synchronized (payloadLoaders) {
            Map<String, WeakReference<PayloadClassLoader>> byPayload =
                    payloadLoaders.computeIfAbsent(parent, ignored -> new LinkedHashMap<>());
            WeakReference<PayloadClassLoader> reference = byPayload.get(loaderId);
            PayloadClassLoader existing = reference == null ? null : reference.get();
            if (existing != null) {
                return existing;
            }
            Path payloadJar = createPayloadJar(loaderId, packages);
            PayloadClassLoader created = new PayloadClassLoader(payloadJar.toUri().toURL(), parent, packages);
            byPayload.put(loaderId, new WeakReference<>(created));
            return created;
        }
    }

    private Path createPayloadJar(String loaderId, List<String> packages) throws IOException {
        List<String> entryPrefixes = packages.stream()
                .map(name -> name.substring(0, name.length() - 1).replace('.', '/') + '/')
                .toList();
        Map<String, byte[]> classes = payloadClasses(entryPrefixes);
        if (classes.isEmpty()) {
            throw new IOException("Pole Agent payload classes are missing: " + loaderId);
        }
        Path jar = Files.createTempFile("pole-agent-" + sanitize(loaderId) + '-', ".jar");
        jar.toFile().deleteOnExit();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }

    private Map<String, byte[]> payloadClasses(List<String> prefixes) throws IOException {
        Path source = Path.of(distributionLocation);
        LinkedHashMap<String, byte[]> classes = new LinkedHashMap<>();
        if (Files.isDirectory(source)) {
            for (String prefix : prefixes) {
                Path root = source.resolve(prefix);
                if (!Files.exists(root)) {
                    continue;
                }
                try (var stream = Files.walk(root)) {
                    for (Path file : stream.filter(path -> path.toString().endsWith(".class")).toList()) {
                        String entryName = source.relativize(file).toString()
                                .replace(source.getFileSystem().getSeparator(), "/");
                        classes.put(entryName, Files.readAllBytes(file));
                    }
                }
            }
        } else {
            try (JarFile jar = new JarFile(source.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class") && matches(entry.getName(), prefixes)) {
                        try (var input = jar.getInputStream(entry)) {
                            classes.put(entry.getName(), input.readAllBytes());
                        }
                    }
                }
            }
        }
        return classes;
    }

    private static URI sourceLocation() {
        try {
            return DefaultPoleAgentContext.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot locate Pole Java Agent distribution", exception);
        }
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizePackage(String name) {
        String normalized = Objects.requireNonNull(name, "child-first package").trim();
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("child-first package must not be blank");
        }
        return normalized + '.';
    }

    private static boolean matches(String entryName, List<String> prefixes) {
        return prefixes.stream().anyMatch(entryName::startsWith);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static final class PayloadClassLoader extends URLClassLoader {
        private final List<String> childFirstPackages;

        private PayloadClassLoader(URL payloadJar, ClassLoader parent, List<String> childFirstPackages) {
            super(new URL[]{payloadJar}, parent);
            this.childFirstPackages = childFirstPackages;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && childFirstPackages.stream().anyMatch(name::startsWith)) {
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
