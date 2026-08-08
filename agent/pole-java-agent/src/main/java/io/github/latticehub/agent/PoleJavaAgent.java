package io.github.latticehub.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarFile;

public final class PoleJavaAgent {
    private static final String BRIDGE_CLASS = "io.github.latticehub.agent.bootstrap.PoleAgentBridge";
    private static final String RUNTIME_CLASS = "io.github.latticehub.agent.core.PoleJavaAgent";
    private static volatile JarFile bootstrapJar;
    private static volatile ClassLoader runtimeClassLoader;

    private PoleJavaAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        try {
            Path agentJar = sourcePath();
            Path agentHome = agentJar.getParent();
            bootstrapJar = new JarFile(agentJar.toFile());
            instrumentation.appendToBootstrapClassLoaderSearch(bootstrapJar);
            Class.forName(BRIDGE_CLASS, true, null);

            URL[] runtimeUrls = runtimeUrls(agentHome.resolve("lib"));
            URLClassLoader loader = new URLClassLoader(runtimeUrls, ClassLoader.getPlatformClassLoader());
            runtimeClassLoader = loader;
            Class<?> runtime = Class.forName(RUNTIME_CLASS, true, loader);
            runtime.getMethod("premain", String.class, Instrumentation.class, String.class)
                    .invoke(null, arguments, instrumentation, agentHome.toString());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Pole Java Agent runtime failed to start", cause);
        } catch (Exception exception) {
            throw new IllegalStateException("Pole Java Agent bootstrap failed to start", exception);
        }
    }

    private static URL[] runtimeUrls(Path libDirectory) throws IOException {
        if (!Files.isDirectory(libDirectory)) {
            throw new IOException("Pole Java Agent lib directory is missing: " + libDirectory);
        }
        try (var files = Files.list(libDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(PoleJavaAgent::toUrl)
                    .toArray(URL[]::new);
        }
    }

    private static Path sourcePath() {
        try {
            URI location = PoleJavaAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Pole Java Agent must run from its bootstrap JAR: " + path);
            }
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("cannot locate Pole Java Agent bootstrap JAR", exception);
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalStateException("invalid Pole Java Agent runtime path: " + path, exception);
        }
    }
}
