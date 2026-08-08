package io.github.latticehub.agent.plugin.grpc;

import io.github.latticehub.agent.api.PoleAgentContext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

final class GrpcInstaller {
    static final String MANAGED_CHANNEL_BUILDER_EVENT = "grpc.managed-channel-builder";
    static final String SERVER_BUILDER_EVENT = "grpc.server-builder";
    private static final String ADAPTER_INSTALLER = "io.github.latticehub.adapter.grpc.PoleGrpcAdapterInstaller";
    private static final List<String> CHILD_FIRST_PACKAGES = List.of("io.github.latticehub.adapter.grpc.");
    private static volatile PoleAgentContext context;

    private GrpcInstaller() {
    }

    static void initialize(PoleAgentContext agentContext) {
        context = Objects.requireNonNull(agentContext, "agentContext");
    }

    static void installClient(Object builder) {
        install(builder, "installClient");
    }

    static void installServer(Object builder) {
        install(builder, "installServer");
    }

    private static void install(Object builder, String methodName) {
        Objects.requireNonNull(builder, "builder");
        try {
            Class<?> installer = context().loadIsolatedClass(
                    "grpc-1x",
                    "grpc-1x-adapter",
                    builder.getClass().getClassLoader(),
                    ADAPTER_INSTALLER,
                    CHILD_FIRST_PACKAGES);
            Method method = installer.getMethod(methodName, Object.class);
            method.invoke(null, builder);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install gRPC adapter", exception.getCause());
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install gRPC adapter", exception);
        }
    }

    private static PoleAgentContext context() {
        PoleAgentContext current = context;
        if (current == null) {
            throw new IllegalStateException("gRPC Agent plugin has not been initialized");
        }
        return current;
    }
}
