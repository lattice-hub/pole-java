package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultPoleAgentContextTest {
    @Test
    void isolatesPayloadPerApplicationClassLoaderAndReusesEachLoader() throws Exception {
        DefaultPoleAgentContext context = new DefaultPoleAgentContext(instrumentation(), sourceLocation());
        ClassLoader firstApplication = new ClassLoader(getClass().getClassLoader()) {
        };
        ClassLoader secondApplication = new ClassLoader(getClass().getClassLoader()) {
        };

        Class<?> first = context.loadIsolatedClass(
                "test", "core", firstApplication, PoleJavaAgent.class.getName(), List.of("io.github.latticehub.agent.core"));
        Class<?> firstAgain = context.loadIsolatedClass(
                "test", "core", firstApplication, PoleJavaAgent.class.getName(), List.of("io.github.latticehub.agent.core"));
        Class<?> second = context.loadIsolatedClass(
                "test", "core", secondApplication, PoleJavaAgent.class.getName(), List.of("io.github.latticehub.agent.core"));

        assertSame(first, firstAgain);
        assertNotSame(first, second);
        assertSame(firstApplication, first.getClassLoader().getParent());
        assertSame(secondApplication, second.getClassLoader().getParent());
        assertSame(PoleAgentPlugin.class, first.getClassLoader().loadClass(PoleAgentPlugin.class.getName()));
    }

    private static Instrumentation instrumentation() {
        return (Instrumentation) Proxy.newProxyInstance(
                DefaultPoleAgentContextTest.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, arguments) -> null);
    }

    private static java.net.URI sourceLocation() throws Exception {
        Path classes = Path.of(DefaultPoleAgentContext.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path bundle = Files.createTempDirectory("pole-agent-plugin-bundle-");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(bundle.resolve("core.jar")));
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String entryName = classes.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return bundle.toUri();
    }
}
