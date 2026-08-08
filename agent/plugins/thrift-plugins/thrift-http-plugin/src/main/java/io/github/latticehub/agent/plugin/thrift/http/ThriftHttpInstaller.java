package io.github.latticehub.agent.plugin.thrift.http;

import io.github.latticehub.agent.api.PoleAgentContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ThriftHttpInstaller {
    static final String CLIENT_EVENT = "thrift-http.client";
    static final String SERVER_ENTER_EVENT = "thrift-http.server.enter";
    static final String SERVER_EXIT_EVENT = "thrift-http.server.exit";
    private static final String ADAPTER_CLASS =
            "io.github.latticehub.adapter.thrift.http.ThriftHttpTrafficContextAdapter";
    private static final Map<ClassLoader, Class<?>> ADAPTERS = new WeakHashMap<>();
    private static final ThreadLocal<Deque<AutoCloseable>> SERVER_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile PoleAgentContext context;

    private ThriftHttpInstaller() {
    }

    static void initialize(PoleAgentContext agentContext) {
        context = agentContext;
    }

    static void injectClient(Object client) {
        invoke(adapter(client), "injectClient", client);
    }

    static void enterServer(Object request) {
        Object scope = invoke(adapter(request), "enterServer", request);
        if (!(scope instanceof AutoCloseable closeable)) {
            throw new IllegalStateException("Thrift HTTP adapter returned an invalid TrafficContext scope");
        }
        SERVER_SCOPES.get().push(closeable);
    }

    static void exitServer() {
        Deque<AutoCloseable> scopes = SERVER_SCOPES.get();
        if (scopes.isEmpty()) {
            throw new IllegalStateException("Thrift HTTP server TrafficContext scope is missing");
        }
        try {
            scopes.pop().close();
        } catch (Exception exception) {
            throw new IllegalStateException("Thrift HTTP server TrafficContext scope failed to close", exception);
        } finally {
            if (scopes.isEmpty()) {
                SERVER_SCOPES.remove();
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
                        "thrift-http",
                        "adapter",
                        loader,
                        ADAPTER_CLASS,
                        List.of("io.github.latticehub.adapter.thrift.http"));
                ADAPTERS.put(loader, loaded);
                return loaded;
            } catch (Exception exception) {
                throw new IllegalStateException("Pole Java Agent failed to load the Thrift HTTP adapter", exception);
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
            throw new IllegalStateException("Pole Java Agent Thrift HTTP adapter invocation failed: " + methodName, cause);
        }
    }

    private static PoleAgentContext context() {
        PoleAgentContext current = context;
        if (current == null) {
            throw new IllegalStateException("Thrift HTTP Agent plugin has not been initialized");
        }
        return current;
    }
}
