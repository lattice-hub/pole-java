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
        Path entryPlugin = springCloudPluginJar("spring-cloud-plugin");
        assertJarContains(entryPlugin, "io/github/latticehub/agent/plugin/springcloud/SpringCloudAgentPlugin.class");
        assertJarContains(entryPlugin, "io/github/latticehub/agent/plugin/springcloud/SpringCloudVersionPlugin.class");
        assertJarContains(entryPlugin,
                "io/github/latticehub/agent/plugin/springcloud/payload/client/SidecarBootstrapClient.class");
        assertJarContains(
                springCloudPluginJar("spring-cloud-3x-plugin"),
                "io/github/latticehub/adapter/springcloud/boot2/PoleSpringBoot2Initializer.class");
        assertJarContains(
                springCloudPluginJar("spring-cloud-4x-plugin"),
                "io/github/latticehub/adapter/springcloud/boot3/PoleSpringBoot3Initializer.class");
        assertJarContains(
                springCloudPluginJar("spring-cloud-5x-plugin"),
                "io/github/latticehub/adapter/springcloud/boot4/PoleSpringBoot4Initializer.class");
        try (JarFile jar = new JarFile(entryPlugin.toFile())) {
            assertNull(jar.getEntry("io/github/latticehub/agent/api/PoleAgentPlugin.class"));
            assertNull(jar.getEntry("io/github/latticehub/agent/bootstrap/PoleAgentBridge.class"));
            assertNull(jar.getEntry("io/github/latticehub/adapter/springcloud/boot2/PoleSpringBoot2Initializer.class"));
            assertNull(jar.getEntry("io/github/latticehub/adapter/springcloud/boot3/PoleSpringBoot3Initializer.class"));
            assertNull(jar.getEntry("io/github/latticehub/adapter/springcloud/boot4/PoleSpringBoot4Initializer.class"));
        }
        Path dubboPlugin = pluginJar("dubbo-plugins", "dubbo-3x-plugin");
        assertJarContains(dubboPlugin, "io/github/latticehub/adapter/dubbo/v3/DubboTrafficContextAdapter.class");
        Path grpcPlugin = pluginJar("grpc-plugins", "grpc-1x-plugin");
        assertJarContains(grpcPlugin, "io/github/latticehub/adapter/grpc/PoleGrpcAdapterInstaller.class");
        Path thriftPlugin = pluginJar("thrift-plugins", "thrift-http-plugin");
        assertJarContains(
                thriftPlugin,
                "io/github/latticehub/adapter/thrift/http/ThriftHttpTrafficContextAdapter.class");
        try (JarFile jar = new JarFile(dubboPlugin.toFile())) {
            assertNull(jar.getEntry("org/apache/dubbo/rpc/Invocation.class"));
        }
        try (JarFile jar = new JarFile(grpcPlugin.toFile())) {
            assertNull(jar.getEntry("io/grpc/ClientInterceptor.class"));
        }
        try (JarFile jar = new JarFile(thriftPlugin.toFile())) {
            assertNull(jar.getEntry("org/apache/thrift/transport/THttpClient.class"));
        }
    }

    private static Path agentJar() {
        return distributionHome().resolve("pole-java-agent.jar");
    }

    private static Path runtimeJar(String artifactId) throws IOException {
        return findJar(distributionHome().resolve("lib"), artifactId);
    }

    private static Path springCloudPluginJar(String artifactId) throws IOException {
        return pluginJar("spring-cloud-plugins", artifactId);
    }

    private static Path pluginJar(String family, String artifactId) throws IOException {
        return findJar(distributionHome().resolve("plugins").resolve(family), artifactId);
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
