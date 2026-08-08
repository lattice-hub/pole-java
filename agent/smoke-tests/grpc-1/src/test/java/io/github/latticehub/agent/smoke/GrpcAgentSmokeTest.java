package io.github.latticehub.agent.smoke;

import io.github.latticehub.client.TrafficContext;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcAgentSmokeTest {
    private ManagedChannel channel;
    private Server server;

    @AfterEach
    void close() throws Exception {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
        TrafficContext.reset();
    }

    @Test
    void agentInstallsClientAndServerInterceptors() throws Exception {
        String name = InProcessServerBuilder.generateName();
        AtomicReference<TrafficContext> observed = new AtomicReference<>();
        MethodDescriptor<String, String> method = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("pole.Echo/echo")
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build();
        ServerServiceDefinition service = ServerServiceDefinition.builder("pole.Echo")
                .addMethod(method, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observed.set(TrafficContext.current().orElseThrow());
                    observer.onNext(request);
                    observer.onCompleted();
                }))
                .build();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        TrafficContext expected = TrafficContext.builder().campaign("canary").lane("gray").build();

        try (TrafficContext.Scope ignored = TrafficContext.attach(expected)) {
            assertEquals("hello", ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, "hello"));
        }
        assertEquals(expected, observed.get());
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }
        @Override public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
