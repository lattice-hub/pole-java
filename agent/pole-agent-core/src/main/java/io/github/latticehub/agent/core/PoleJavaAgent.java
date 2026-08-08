package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public final class PoleJavaAgent {
    private PoleJavaAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        PoleAgentContext context = new DefaultPoleAgentContext(instrumentation);
        installPlugins(context, ServiceLoader.load(PoleAgentPlugin.class, PoleAgentPlugin.class.getClassLoader()));
    }

    static void installPlugins(PoleAgentContext context, Iterable<PoleAgentPlugin> discoveredPlugins) {
        List<PoleAgentPlugin> plugins = new ArrayList<>();
        discoveredPlugins.forEach(plugins::add);
        plugins.sort(Comparator.comparing(PoleJavaAgent::pluginId));

        Set<String> installedIds = new HashSet<>();
        for (PoleAgentPlugin plugin : plugins) {
            String id = pluginId(plugin);
            if (!installedIds.add(id)) {
                throw new IllegalStateException("duplicate Pole Java Agent plugin id: " + id);
            }
            try {
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
}
