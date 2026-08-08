package io.github.latticehub.adapter.grpc;

import io.github.latticehub.client.TrafficContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleGrpcInterceptorsTest {
    private static final Metadata.Key<String> BAGGAGE = Metadata.Key.of("baggage", Metadata.ASCII_STRING_MARSHALLER);
    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.Service/Call")
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    @AfterEach
    void resetTrafficContext() {
        TrafficContext.reset();
    }

    @Test
    void clientWritesTrafficContextAsW3CBaggage() {
        RecordingClientCall call = new RecordingClientCall();
        Metadata headers = new Metadata();
        headers.put(BAGGAGE, "vendor.trace=keep");

        try (TrafficContext.Scope ignored = TrafficContext.attach(TrafficContext.builder()
                .campaign("release")
                .lane("blue")
                .bucket(17)
                .build())) {
            PoleGrpcClientInterceptor.INSTANCE.interceptCall(METHOD, CallOptions.DEFAULT, new RecordingChannel(call))
                    .start(new ClientCall.Listener<>() { }, headers);
        }

        assertEquals("vendor.trace=keep,latticehub.traffic.version=1,latticehub.traffic.campaign=release,"
                + "latticehub.traffic.lane=blue,latticehub.traffic.bucket=17", call.headers.get(BAGGAGE));
    }

    @Test
    void serverRestoresTrafficContextForEveryListenerCallback() {
        Metadata headers = new Metadata();
        headers.put(BAGGAGE, "latticehub.traffic.version=1,latticehub.traffic.campaign=release,latticehub.traffic.lane=blue");
        List<TrafficContext> observed = new ArrayList<>();
        ServerCall.Listener<String> listener = PoleGrpcServerInterceptor.INSTANCE.interceptCall(
                new RecordingServerCall(), headers, recordingHandler(observed));

        invokeWithStaleContext(() -> listener.onMessage("request"));
        invokeWithStaleContext(listener::onHalfClose);
        invokeWithStaleContext(listener::onCancel);
        invokeWithStaleContext(listener::onComplete);
        invokeWithStaleContext(listener::onReady);

        assertEquals(5, observed.size());
        assertTrue(observed.stream().allMatch(context -> context.getCampaign().orElseThrow().equals("release")));
        assertTrue(observed.stream().allMatch(context -> context.getLane().orElseThrow().equals("blue")));
    }

    @Test
    void serverRejectsInvalidTrafficBaggage() {
        Metadata headers = new Metadata();
        headers.put(BAGGAGE, "latticehub.traffic.version=2");
        RecordingServerCall call = new RecordingServerCall();

        ServerCall.Listener<String> listener = PoleGrpcServerInterceptor.INSTANCE.interceptCall(
                call, headers, recordingHandler(new ArrayList<>()));

        listener.onHalfClose();
        assertEquals(Status.Code.INVALID_ARGUMENT, call.closedStatus.getCode());
    }

    @Test
    void installerConnectsClientAndServerInterceptorsToRealGrpcBuilders() throws Exception {
        String serverName = "pole-grpc-test-" + UUID.randomUUID();
        AtomicReference<TrafficContext> observed = new AtomicReference<>();
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerServiceDefinition.builder("test.Service")
                        .addMethod(METHOD, ServerCalls.asyncUnaryCall((String request, StreamObserver<String> response) -> {
                            observed.set(TrafficContext.current().orElseThrow());
                            response.onNext("response");
                            response.onCompleted();
                        }))
                        .build());
        PoleGrpcAdapterInstaller.installServer(serverBuilder);
        io.grpc.Server server = serverBuilder.build();
        try {
            server.start();
            io.grpc.ManagedChannelBuilder<?> clientBuilder = InProcessChannelBuilder.forName(serverName).directExecutor();
            PoleGrpcAdapterInstaller.installClient(clientBuilder);
            io.grpc.ManagedChannel channel = clientBuilder.build();
            try (TrafficContext.Scope ignored = TrafficContext.attach(TrafficContext.builder().campaign("release").build())) {
                ClientCalls.blockingUnaryCall(channel, METHOD, CallOptions.DEFAULT, "request");
            } finally {
                channel.shutdownNow();
            }
            assertEquals("release", observed.get().getCampaign().orElseThrow());
        } finally {
            server.shutdownNow();
        }
    }

    private static ServerCallHandler<String, String> recordingHandler(List<TrafficContext> observed) {
        return (call, headers) -> new ServerCall.Listener<>() {
            @Override public void onMessage(String message) { observed.add(TrafficContext.current().orElseThrow()); }
            @Override public void onHalfClose() { observed.add(TrafficContext.current().orElseThrow()); }
            @Override public void onCancel() { observed.add(TrafficContext.current().orElseThrow()); }
            @Override public void onComplete() { observed.add(TrafficContext.current().orElseThrow()); }
            @Override public void onReady() { observed.add(TrafficContext.current().orElseThrow()); }
        };
    }

    private static void invokeWithStaleContext(Runnable action) {
        try (TrafficContext.Scope ignored = TrafficContext.attach(TrafficContext.builder().campaign("stale").build())) {
            action.run();
            assertEquals("stale", TrafficContext.current().orElseThrow().getCampaign().orElseThrow());
        }
        assertFalse(TrafficContext.current().isPresent());
    }

    private static final class RecordingChannel extends Channel {
        private final RecordingClientCall call;

        private RecordingChannel(RecordingClientCall call) {
            this.call = call;
        }

        @Override public String authority() { return "test"; }
        @Override public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> method, CallOptions callOptions) {
            @SuppressWarnings("unchecked") ClientCall<RequestT, ResponseT> typed = (ClientCall<RequestT, ResponseT>) call;
            return typed;
        }
    }

    private static final class RecordingClientCall extends ClientCall<String, String> {
        private Metadata headers;

        @Override public void start(Listener<String> responseListener, Metadata headers) { this.headers = headers; }
        @Override public void request(int count) { }
        @Override public void cancel(String message, Throwable cause) { }
        @Override public void halfClose() { }
        @Override public void sendMessage(String message) { }
    }

    private static final class RecordingServerCall extends ServerCall<String, String> {
        private Status closedStatus;

        @Override public void request(int count) { }
        @Override public void sendHeaders(Metadata headers) { }
        @Override public void sendMessage(String message) { }
        @Override public void close(Status status, Metadata trailers) { closedStatus = status; }
        @Override public boolean isCancelled() { return false; }
        @Override public MethodDescriptor<String, String> getMethodDescriptor() { return METHOD; }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override public InputStream stream(String value) { return InputStream.nullInputStream(); }
        @Override public String parse(InputStream stream) { return ""; }
    }
}
