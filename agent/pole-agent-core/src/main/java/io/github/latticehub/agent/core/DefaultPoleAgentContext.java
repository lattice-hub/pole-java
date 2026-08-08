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
    private final List<JarFile> systemPayloadJars = new java.util.ArrayList<>();
    private final Map<ClassLoader, Map<String, WeakReference<PayloadClassLoader>>> payloadLoaders = new WeakHashMap<>();

    DefaultPoleAgentContext(Instrumentation instrumentation, URI distributionLocation) {
        this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
        this.distributionLocation = Objects.requireNonNull(distributionLocation, "distributionLocation");
    }

    @Override
    public Instrumentation instrumentation() {
        return instrumentation;
    }

    @Override
    public void appendPluginPayloadToSystemClassLoader(Collection<String> packages) throws IOException {
        List<String> prefixes = classEntryPrefixes(packages);
        Path source = Path.of(distributionLocation);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Pole Agent plugin payload must come from a JAR: " + source);
        }
        Path payloadJar = Files.createTempFile("pole-agent-system-payload-", ".jar");
        payloadJar.toFile().deleteOnExit();
        try (JarFile pluginJar = new JarFile(source.toFile());
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(payloadJar))) {
            var entries = pluginJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !includeSystemPayloadEntry(entry.getName(), prefixes)) {
                    continue;
                }
                output.putNextEntry(new JarEntry(entry.getName()));
                try (var input = pluginJar.getInputStream(entry)) {
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
        JarFile opened = new JarFile(payloadJar.toFile());
        systemPayloadJars.add(opened);
        instrumentation.appendToSystemClassLoaderSearch(opened);
    }

    @Override
    public Map<String, byte[]> pluginClassBytes(Collection<String> packages) throws IOException {
        List<String> prefixes = classEntryPrefixes(packages);
        Path source = Path.of(distributionLocation);
        LinkedHashMap<String, byte[]> classes = new LinkedHashMap<>();
        if (Files.isDirectory(source)) {
            for (String prefix : prefixes) {
                Path root = source.resolve(prefix);
                if (!Files.exists(root)) {
                    continue;
                }
                try (var files = Files.walk(root)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                        String className = source.relativize(file).toString()
                                .replace(source.getFileSystem().getSeparator(), ".")
                                .replaceAll("\\.class$", "");
                        classes.put(className, Files.readAllBytes(file));
                    }
                }
            }
        } else {
            try (JarFile jar = new JarFile(source.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")
                            && prefixes.stream().anyMatch(entry.getName()::startsWith)) {
                        String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                        try (var input = jar.getInputStream(entry)) {
                            classes.put(className, input.readAllBytes());
                        }
                    }
                }
            }
        }
        return Map.copyOf(classes);
    }

    private static List<String> classEntryPrefixes(Collection<String> packages) {
        List<String> prefixes = packages.stream()
                .map(DefaultPoleAgentContext::normalizePackage)
                .distinct()
                .map(name -> name.substring(0, name.length() - 1).replace('.', '/') + '/')
                .toList();
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("packages must not be empty");
        }
        return prefixes;
    }

    private static boolean includeSystemPayloadEntry(String entryName, List<String> prefixes) {
        if (entryName.startsWith("META-INF/services/")) {
            return !entryName.equals("META-INF/services/io.github.latticehub.agent.api.PoleAgentPlugin");
        }
        return entryName.endsWith(".class") && prefixes.stream().anyMatch(entryName::startsWith);
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
            PayloadClassLoader created = new PayloadClassLoader(distributionLocation.toURL(), parent, packages);
            byPayload.put(loaderId, new WeakReference<>(created));
            return created;
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
