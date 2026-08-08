package io.github.latticehub.agent.api;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Map;

public interface PoleAgentContext {
    Instrumentation instrumentation();

    void appendPluginPayloadToSystemClassLoader(Collection<String> packages) throws IOException;

    Map<String, byte[]> pluginClassBytes(Collection<String> packages) throws IOException;

    Class<?> loadIsolatedClass(
            String pluginId,
            String payloadId,
            ClassLoader applicationClassLoader,
            String className,
            Collection<String> childFirstPackages) throws IOException, ClassNotFoundException;
}
