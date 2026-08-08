package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringCloudInstallerTest {
    @Test
    void parsesSupportedBootMajorVersions() {
        assertEquals(2, SpringCloudInstaller.bootMajor("2.7.18"));
        assertEquals(3, SpringCloudInstaller.bootMajor("3.5.16"));
        assertEquals(4, SpringCloudInstaller.bootMajor("4.1.0"));
    }

    @Test
    void rejectsMissingOrInvalidVersions() {
        assertThrows(IllegalStateException.class, () -> SpringCloudInstaller.bootMajor(null));
        assertThrows(IllegalStateException.class, () -> SpringCloudInstaller.bootMajor("development"));
    }

    @Test
    void publishesPluginThroughServiceLoader() {
        List<PoleAgentPlugin> plugins = ServiceLoader
                .load(PoleAgentPlugin.class, SpringCloudAgentPlugin.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertEquals(List.of("spring-cloud"), plugins.stream().map(PoleAgentPlugin::id).toList());
    }
}
