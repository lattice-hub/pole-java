package io.github.latticehub.agent.smoke;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentDistributionSmokeTest {
    private static final String SERVICE_DESCRIPTOR =
            "META-INF/services/io.github.latticehub.agent.api.PoleAgentPlugin";

    @Test
    void shadedJarContainsAgentCorePluginAndVersionedPayloads() throws IOException {
        try (JarFile jar = new JarFile(agentJar().toFile())) {
            assertEquals(
                    "io.github.latticehub.agent.PoleJavaAgent",
                    jar.getManifest().getMainAttributes().getValue("Premain-Class"));
            assertEntry(jar, "io/github/latticehub/agent/PoleJavaAgent.class");
            assertEntry(jar, "io/github/latticehub/agent/api/PoleAgentPlugin.class");
            assertEntry(jar, "io/github/latticehub/agent/core/PoleJavaAgent.class");
            assertEntry(jar, "io/github/latticehub/agent/plugin/springcloud/SpringCloudAgentPlugin.class");
            assertEntry(jar, "io/github/latticehub/adapter/springcloud/boot2/PoleSpringBoot2Initializer.class");
            assertEntry(jar, "io/github/latticehub/adapter/springcloud/boot3/PoleSpringBoot3Initializer.class");
            assertEntry(jar, "io/github/latticehub/adapter/springcloud/boot4/PoleSpringBoot4Initializer.class");
            assertNull(jar.getEntry("META-INF/spring.factories"));
            assertNull(jar.getEntry("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));

            List<String> providers;
            try (var input = jar.getInputStream(jar.getEntry(SERVICE_DESCRIPTOR))) {
                providers = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .filter(line -> !line.isBlank())
                        .toList();
            }
            assertEquals(List.of("io.github.latticehub.agent.plugin.springcloud.SpringCloudAgentPlugin"), providers);
        }
    }

    private static Path agentJar() throws IOException {
        try (var files = Files.list(Path.of("target", "agent"))) {
            return files.filter(path -> path.getFileName().toString().matches("pole-java-agent-.+\\.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("copied Pole Java Agent JAR is missing"));
        }
    }

    private static void assertEntry(JarFile jar, String entryName) {
        assertNotNull(jar.getEntry(entryName), entryName);
    }
}
