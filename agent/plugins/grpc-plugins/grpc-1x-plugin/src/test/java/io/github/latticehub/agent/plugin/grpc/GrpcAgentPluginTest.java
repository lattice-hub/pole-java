package io.github.latticehub.agent.plugin.grpc;

import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcAgentPluginTest {
    @Test
    void publishesGrpcPluginThroughServiceLoader() {
        List<PoleAgentPlugin> plugins = ServiceLoader.load(PoleAgentPlugin.class, GrpcAgentPlugin.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertEquals(List.of("grpc-1x"), plugins.stream().map(PoleAgentPlugin::id).toList());
    }

    @Test
    void rejectsInstallerUseBeforeInitialization() {
        assertThrows(IllegalStateException.class, () -> GrpcInstaller.installClient(new Object()));
    }
}
