package io.github.latticehub.agent.plugin.dubbo.v3;

import io.github.latticehub.agent.api.PoleAgentContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class Dubbo3xInstaller {
    static final String CONSUMER_EVENT = "dubbo-3x.consumer";
    static final String PROVIDER_ENTER_EVENT = "dubbo-3x.provider.enter";
    static final String PROVIDER_EXIT_EVENT = "dubbo-3x.provider.exit";
    private static final String ADAPTER_CLASS = "io.github.latticehub.adapter.dubbo.v3.DubboTrafficContextAdapter";
    private static final Map<ClassLoader, Class<?>> ADAPTERS = new WeakHashMap<>();
    private static final ThreadLocal<Deque<AutoCloseable>> PROVIDER_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile PoleAgentContext context;

    private Dubbo3xInstaller() {
    }

    static void initialize(PoleAgentContext agentContext) {
        context = agentContext;
    }

    static void injectConsumer(Object invocation) {
        invoke(adapter(invocation), "inject", invocation);
    }

    static void enterProvider(Object invocation) {
        Object scope = invoke(adapter(invocation), "enterProvider", invocation);
        if (!(scope instanceof AutoCloseable closeable)) {
            throw new IllegalStateException("Dubbo adapter returned an invalid TrafficContext scope");
        }
        PROVIDER_SCOPES.get().push(closeable);
    }

    static void exitProvider() {
        Deque<AutoCloseable> scopes = PROVIDER_SCOPES.get();
        if (scopes.isEmpty()) {
            throw new IllegalStateException("Dubbo provider TrafficContext scope is missing");
        }
        try {
            scopes.pop().close();
        } catch (Exception exception) {
            throw new IllegalStateException("Dubbo provider TrafficContext scope failed to close", exception);
        } finally {
            if (scopes.isEmpty()) {
                PROVIDER_SCOPES.remove();
            }
        }
    }

    private static Class<?> adapter(Object target) {
        ClassLoader loader = target.getClass().getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        synchronized (ADAPTERS) {
            Class<?> existing = ADAPTERS.get(loader);
            if (existing != null) {
                return existing;
            }
            try {
                Class<?> loaded = context().loadIsolatedClass(
                        "dubbo-3x", "adapter", loader, ADAPTER_CLASS, List.of("io.github.latticehub.adapter.dubbo.v3"));
                ADAPTERS.put(loader, loaded);
                return loaded;
            } catch (Exception exception) {
                throw new IllegalStateException("Pole Java Agent failed to load the Dubbo 3.x adapter", exception);
            }
        }
    }

    private static Object invoke(Class<?> adapter, String methodName, Object argument) {
        try {
            Method method = adapter.getMethod(methodName, Object.class);
            return method.invoke(null, argument);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause()
                    : exception;
            throw new IllegalStateException("Pole Java Agent Dubbo adapter invocation failed: " + methodName, cause);
        }
    }

    private static PoleAgentContext context() {
        PoleAgentContext current = context;
        if (current == null) {
            throw new IllegalStateException("Dubbo 3.x Agent plugin has not been initialized");
        }
        return current;
    }
}
