package io.github.latticehub.adapter.grpc;

import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class PoleGrpcAdapterInstaller {
    private static final Set<Object> CLIENT_BUILDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Object> SERVER_BUILDERS = Collections.newSetFromMap(new WeakHashMap<>());

    private PoleGrpcAdapterInstaller() {
    }

    public static void installClient(Object builder) {
        if (!(builder instanceof ManagedChannelBuilder<?> channelBuilder)) {
            throw new IllegalArgumentException("expected a gRPC ManagedChannelBuilder: " + builder.getClass().getName());
        }
        synchronized (CLIENT_BUILDERS) {
            if (CLIENT_BUILDERS.add(builder)) {
                channelBuilder.intercept(PoleGrpcClientInterceptor.INSTANCE);
            }
        }
    }

    public static void installServer(Object builder) {
        if (!(builder instanceof ServerBuilder<?> serverBuilder)) {
            throw new IllegalArgumentException("expected a gRPC ServerBuilder: " + builder.getClass().getName());
        }
        synchronized (SERVER_BUILDERS) {
            if (SERVER_BUILDERS.add(builder)) {
                serverBuilder.intercept(PoleGrpcServerInterceptor.INSTANCE);
            }
        }
    }
}
