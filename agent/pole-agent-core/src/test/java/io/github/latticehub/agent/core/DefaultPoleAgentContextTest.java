package io.github.latticehub.agent.core;

import io.github.latticehub.agent.api.PoleAgentPlugin;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.List;

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
        return DefaultPoleAgentContext.class.getProtectionDomain().getCodeSource().getLocation().toURI();
    }
}
