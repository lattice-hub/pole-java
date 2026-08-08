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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        Path payloadJar = Files.createTempFile("pole-agent-system-payload-", ".jar");
        payloadJar.toFile().deleteOnExit();
        Set<String> writtenEntries = new HashSet<>();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(payloadJar))) {
            for (Path archive : pluginArchives()) {
                try (JarFile pluginJar = new JarFile(archive.toFile())) {
                    var entries = pluginJar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.isDirectory()
                                || !includeSystemPayloadEntry(entry.getName(), prefixes)
                                || !writtenEntries.add(entry.getName())) {
                            continue;
                        }
                        output.putNextEntry(new JarEntry(entry.getName()));
                        try (var input = pluginJar.getInputStream(entry)) {
                            input.transferTo(output);
                        }
                        output.closeEntry();
                    }
                }
            }
        }
        JarFile opened = new JarFile(payloadJar.toFile());
        systemPayloadJars.add(opened);
        instrumentation.appendToSystemClassLoaderSearch(opened);
    }

    @Override
    public Map<String, byte[]> pluginClassBytes(Collection<String> packages) throws IOException {
        List<String> prefixes = classEntryPrefixes(packages);
        LinkedHashMap<String, byte[]> classes = new LinkedHashMap<>();
        for (Path archive : pluginArchives()) {
            try (JarFile jar = new JarFile(archive.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")
                            && prefixes.stream().anyMatch(entry.getName()::startsWith)) {
                        String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                        try (var input = jar.getInputStream(entry)) {
                            byte[] classBytes = input.readAllBytes();
                            byte[] existing = classes.putIfAbsent(className, classBytes);
                            if (existing != null && !Arrays.equals(existing, classBytes)) {
                                throw new IOException("duplicate Pole Agent plugin payload class: " + className);
                            }
                        }
                    }
                }
            }
        }
        return Map.copyOf(classes);
    }

    private List<Path> pluginArchives() throws IOException {
        Path source = Path.of(distributionLocation);
        if (Files.isRegularFile(source)) {
            return List.of(source);
        }
        if (Files.isDirectory(source)) {
            try (var files = Files.list(source)) {
                List<Path> archives = files
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                if (!archives.isEmpty()) {
                    return archives;
                }
            }
        }
        throw new IOException("Pole Agent plugin bundle contains no JARs: " + source);
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
            return !entryName.startsWith("META-INF/services/io.github.latticehub.agent.");
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
            URL[] pluginUrls = pluginArchives().stream()
                    .map(DefaultPoleAgentContext::toUrl)
                    .toArray(URL[]::new);
            PayloadClassLoader created = new PayloadClassLoader(pluginUrls, parent, packages);
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

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalStateException("invalid Pole Agent plugin payload path: " + path, exception);
        }
    }

    private static final class PayloadClassLoader extends URLClassLoader {
        private final List<String> childFirstPackages;

        private PayloadClassLoader(URL[] payloadJars, ClassLoader parent, List<String> childFirstPackages) {
            super(payloadJars, parent);
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
