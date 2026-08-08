package io.github.latticehub.agent.api;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Collection;

public interface PoleAgentContext {
    Instrumentation instrumentation();

    Class<?> loadIsolatedClass(
            String pluginId,
            String payloadId,
            ClassLoader applicationClassLoader,
            String className,
            Collection<String> childFirstPackages) throws IOException, ClassNotFoundException;
}
