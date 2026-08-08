package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;

import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public final class PoleJavaAgent {
    private static final List<ClassLoader> PLUGIN_CLASS_LOADERS = new ArrayList<>();

    private PoleJavaAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation, String agentHome) {
        List<DiscoveredPlugin> plugins = discoverPlugins(Path.of(agentHome).resolve("plugins"));
        installPlugins(instrumentation, plugins);
    }

    static List<DiscoveredPlugin> discoverPlugins(Path pluginDirectory) {
        if (!Files.isDirectory(pluginDirectory)) {
            throw new IllegalStateException("Pole Java Agent plugin directory is missing: " + pluginDirectory);
        }
        try (var files = Files.list(pluginDirectory)) {
            List<DiscoveredPlugin> plugins = new ArrayList<>();
            for (Path pluginBundle : files.filter(PoleJavaAgent::isPluginBundle)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                URL[] pluginUrls = pluginJars(pluginBundle).stream()
                        .map(PoleJavaAgent::toUrl)
                        .toArray(URL[]::new);
                if (pluginUrls.length == 0) {
                    throw new IllegalStateException("Pole Java Agent plugin bundle is empty: " + pluginBundle);
                }
                PluginClassLoader loader = new PluginClassLoader(pluginUrls, PoleAgentPlugin.class.getClassLoader());
                PLUGIN_CLASS_LOADERS.add(loader);
                ServiceLoader.load(PoleAgentPlugin.class, loader)
                        .forEach(plugin -> plugins.add(new DiscoveredPlugin(plugin, pluginBundle.toUri())));
            }
            return plugins;
        } catch (Exception exception) {
            throw new IllegalStateException("Pole Java Agent plugins failed to load from: " + pluginDirectory, exception);
        }
    }

    private static boolean isPluginBundle(Path path) {
        return Files.isDirectory(path) || path.getFileName().toString().endsWith(".jar");
    }

    private static List<Path> pluginJars(Path pluginBundle) throws java.io.IOException {
        if (Files.isRegularFile(pluginBundle)) {
            return List.of(pluginBundle);
        }
        try (var files = Files.list(pluginBundle)) {
            return files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalStateException("invalid Pole Java Agent plugin path: " + path, exception);
        }
    }

    static void installPlugins(Instrumentation instrumentation, Iterable<DiscoveredPlugin> discoveredPlugins) {
        List<DiscoveredPlugin> plugins = new ArrayList<>();
        discoveredPlugins.forEach(plugins::add);
        plugins.sort(Comparator.comparing(discovered -> pluginId(discovered.plugin())));

        Set<String> installedIds = new HashSet<>();
        for (DiscoveredPlugin discovered : plugins) {
            PoleAgentPlugin plugin = discovered.plugin();
            String id = pluginId(plugin);
            if (!installedIds.add(id)) {
                throw new IllegalStateException("duplicate Pole Java Agent plugin id: " + id);
            }
            try {
                PoleAgentContext context = new DefaultPoleAgentContext(instrumentation, discovered.source());
                plugin.install(context);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Pole Java Agent plugin failed to install: " + id, exception);
            }
        }
    }

    private static String pluginId(PoleAgentPlugin plugin) {
        String id = plugin.id();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Pole Java Agent plugin id must not be blank: " + plugin.getClass().getName());
        }
        return id;
    }

    record DiscoveredPlugin(PoleAgentPlugin plugin, URI source) {
    }

    private static final class PluginClassLoader extends URLClassLoader {
        private static final List<String> PARENT_FIRST_PACKAGES = List.of(
                "java.",
                "javax.",
                "jdk.",
                "sun.",
                "io.github.latticehub.agent.api.",
                "io.github.latticehub.agent.bootstrap.");

        private PluginClassLoader(URL[] pluginJars, ClassLoader parent) {
            super(pluginJars, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && PARENT_FIRST_PACKAGES.stream().noneMatch(name::startsWith)) {
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
