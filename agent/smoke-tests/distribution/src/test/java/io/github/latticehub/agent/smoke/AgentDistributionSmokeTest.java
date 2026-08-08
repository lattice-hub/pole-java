package io.github.latticehub.agent.smoke;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentDistributionSmokeTest {
    @Test
    void distributionSeparatesBootstrapCoreAndPluginPayloads() throws IOException {
        try (JarFile jar = new JarFile(agentJar().toFile())) {
            assertEquals(
                    "io.github.latticehub.agent.PoleJavaAgent",
                    jar.getManifest().getMainAttributes().getValue("Premain-Class"));
            assertEntry(jar, "io/github/latticehub/agent/PoleJavaAgent.class");
            assertEntry(jar, "io/github/latticehub/agent/bootstrap/PoleAgentBridge.class");
            assertNull(jar.getEntry("io/github/latticehub/agent/api/PoleAgentPlugin.class"));
            assertNull(jar.getEntry("io/github/latticehub/agent/core/PoleJavaAgent.class"));
            assertNull(jar.getEntry("io/github/latticehub/agent/plugin/springcloud/SpringCloudAgentPlugin.class"));
        }

        assertJarContains(runtimeJar("pole-agent-api"), "io/github/latticehub/agent/api/PoleAgentPlugin.class");
        assertJarContains(runtimeJar("pole-agent-core"), "io/github/latticehub/agent/core/PoleJavaAgent.class");
        Path pluginJar = pluginJar("pole-agent-plugin-spring-cloud");
        assertJarContains(pluginJar, "io/github/latticehub/agent/plugin/springcloud/SpringCloudAgentPlugin.class");
        assertJarContains(pluginJar, "io/github/latticehub/adapter/springcloud/boot2/PoleSpringBoot2Initializer.class");
        assertJarContains(pluginJar, "io/github/latticehub/adapter/springcloud/boot3/PoleSpringBoot3Initializer.class");
        assertJarContains(pluginJar, "io/github/latticehub/adapter/springcloud/boot4/PoleSpringBoot4Initializer.class");
        assertJarContains(pluginJar,
                "io/github/latticehub/agent/plugin/springcloud/payload/client/SidecarBootstrapClient.class");
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            assertNull(jar.getEntry("io/github/latticehub/agent/api/PoleAgentPlugin.class"));
            assertNull(jar.getEntry("io/github/latticehub/agent/bootstrap/PoleAgentBridge.class"));
        }
    }

    private static Path agentJar() {
        return distributionHome().resolve("pole-java-agent.jar");
    }

    private static Path runtimeJar(String artifactId) throws IOException {
        return findJar(distributionHome().resolve("lib"), artifactId);
    }

    private static Path pluginJar(String artifactId) throws IOException {
        return findJar(distributionHome().resolve("plugins"), artifactId);
    }

    private static Path findJar(Path directory, String artifactId) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith(artifactId + "-"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Pole Java Agent artifact is missing: " + artifactId));
        }
    }

    private static Path distributionHome() {
        return Path.of("target", "agent", "pole-java-agent");
    }

    private static void assertJarContains(Path path, String entryName) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            assertEntry(jar, entryName);
            assertNull(jar.getEntry("META-INF/spring.factories"));
            assertNull(jar.getEntry("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        }
    }

    private static void assertEntry(JarFile jar, String entryName) {
        assertNotNull(jar.getEntry(entryName), entryName);
    }
}
