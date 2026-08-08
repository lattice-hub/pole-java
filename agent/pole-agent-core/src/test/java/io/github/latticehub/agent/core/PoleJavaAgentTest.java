package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleJavaAgentTest {
    private static final URI SOURCE = URI.create("file:/pole-agent-plugin-test.jar");

    @Test
    void installsPluginsInStableIdOrder() {
        List<String> installed = new ArrayList<>();

        PoleJavaAgent.installPlugins(instrumentation(), List.of(
                discovered(plugin("zeta", installed)), discovered(plugin("alpha", installed))));

        assertEquals(List.of("alpha", "zeta"), installed);
    }

    @Test
    void rejectsDuplicateAndBlankPluginIds() {
        assertThrows(IllegalStateException.class, () -> PoleJavaAgent.installPlugins(
                instrumentation(), List.of(
                        discovered(plugin("same", new ArrayList<>())),
                        discovered(plugin("same", new ArrayList<>())))));
        assertThrows(IllegalStateException.class, () -> PoleJavaAgent.installPlugins(
                instrumentation(), List.of(discovered(plugin(" ", new ArrayList<>())))));
    }

    @Test
    void reportsCheckedPluginFailureWithPluginId() {
        PoleAgentPlugin failing = new PoleAgentPlugin() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public void install(PoleAgentContext context) throws Exception {
                throw new IOException("boom");
            }
        };

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> PoleJavaAgent.installPlugins(instrumentation(), List.of(discovered(failing))));

        assertTrue(failure.getMessage().contains("broken"));
    }

    @Test
    void discoversEntryPluginFromPluginFamilyDirectory() throws Exception {
        Path plugins = Files.createTempDirectory("pole-agent-plugins-");
        Path family = Files.createDirectory(plugins.resolve("test-plugins"));
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(family.resolve("test-plugin.jar")))) {
            output.putNextEntry(new JarEntry("META-INF/services/" + PoleAgentPlugin.class.getName()));
            output.write(TestPlugin.class.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }

        List<PoleJavaAgent.DiscoveredPlugin> pluginsFound = PoleJavaAgent.discoverPlugins(plugins);

        assertEquals(List.of("test"), pluginsFound.stream().map(plugin -> plugin.plugin().id()).toList());
        assertEquals(family.toUri(), pluginsFound.get(0).source());
    }

    private static PoleAgentPlugin plugin(String id, List<String> installed) {
        return new PoleAgentPlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void install(PoleAgentContext context) {
                installed.add(id);
            }
        };
    }

    private static PoleJavaAgent.DiscoveredPlugin discovered(PoleAgentPlugin plugin) {
        return new PoleJavaAgent.DiscoveredPlugin(plugin, SOURCE);
    }

    private static Instrumentation instrumentation() {
        return (Instrumentation) Proxy.newProxyInstance(
                PoleJavaAgentTest.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, arguments) -> null);
    }

    public static final class TestPlugin implements PoleAgentPlugin {
        @Override
        public String id() {
            return "test";
        }

        @Override
        public void install(PoleAgentContext context) {
        }
    }
}
