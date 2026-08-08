package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleJavaAgentTest {
    private static final PoleAgentContext CONTEXT = new PoleAgentContext() {
        @Override
        public Instrumentation instrumentation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<?> loadIsolatedClass(
                String pluginId,
                String payloadId,
                ClassLoader applicationClassLoader,
                String className,
                Collection<String> childFirstPackages) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void installsPluginsInStableIdOrder() {
        List<String> installed = new ArrayList<>();

        PoleJavaAgent.installPlugins(CONTEXT, List.of(plugin("zeta", installed), plugin("alpha", installed)));

        assertEquals(List.of("alpha", "zeta"), installed);
    }

    @Test
    void rejectsDuplicateAndBlankPluginIds() {
        assertThrows(IllegalStateException.class, () -> PoleJavaAgent.installPlugins(
                CONTEXT, List.of(plugin("same", new ArrayList<>()), plugin("same", new ArrayList<>()))));
        assertThrows(IllegalStateException.class, () -> PoleJavaAgent.installPlugins(
                CONTEXT, List.of(plugin(" ", new ArrayList<>()))));
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
                () -> PoleJavaAgent.installPlugins(CONTEXT, List.of(failing)));

        assertTrue(failure.getMessage().contains("broken"));
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
}
