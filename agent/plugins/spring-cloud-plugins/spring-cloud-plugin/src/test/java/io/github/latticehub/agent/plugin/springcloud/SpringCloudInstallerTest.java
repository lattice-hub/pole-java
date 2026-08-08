package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringCloudInstallerTest {
    @Test
    void parsesMajorVersions() {
        assertEquals(2, SpringCloudInstaller.majorVersion("2.7.18", "Spring Boot"));
        assertEquals(4, SpringCloudInstaller.majorVersion("4.3.0", "Spring Cloud"));
    }

    @Test
    void rejectsMissingOrInvalidVersions() {
        assertThrows(IllegalStateException.class,
                () -> SpringCloudInstaller.majorVersion(null, "Spring Boot"));
        assertThrows(IllegalStateException.class,
                () -> SpringCloudInstaller.majorVersion("development", "Spring Boot"));
    }

    @Test
    void selectsExactlyOneCompatibleVersionPlugin() {
        SpringCloudVersionPlugin cloud3 = versionPlugin("3x", 2, 3);
        SpringCloudVersionPlugin cloud4 = versionPlugin("4x", 3, 4);

        assertEquals(cloud3, SpringCloudInstaller.selectVersionPlugin(List.of(cloud4, cloud3), 2, 3));
        assertEquals(cloud4, SpringCloudInstaller.selectVersionPlugin(List.of(cloud4, cloud3), 3, null));
        assertThrows(IllegalStateException.class,
                () -> SpringCloudInstaller.selectVersionPlugin(List.of(cloud3, cloud4), 3, 5));
        assertThrows(IllegalStateException.class,
                () -> SpringCloudInstaller.selectVersionPlugin(List.of(cloud3, cloud3), 2, 3));
    }

    @Test
    void publishesEntryPluginThroughServiceLoader() {
        List<PoleAgentPlugin> plugins = ServiceLoader
                .load(PoleAgentPlugin.class, SpringCloudAgentPlugin.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertEquals(List.of("spring-cloud"), plugins.stream().map(PoleAgentPlugin::id).toList());
    }

    private static SpringCloudVersionPlugin versionPlugin(String id, int bootMajor, int cloudMajor) {
        return new SpringCloudVersionPlugin() {
            @Override public String id() { return id; }
            @Override public int springBootMajor() { return bootMajor; }
            @Override public int springCloudMajor() { return cloudMajor; }
            @Override public String adapterPackage() { return "test"; }
            @Override public String initializerClassName() { return "test.Initializer"; }
        };
    }
}
