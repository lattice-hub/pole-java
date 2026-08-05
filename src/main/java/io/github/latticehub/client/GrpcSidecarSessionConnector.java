package io.github.latticehub.client;

import io.grpc.ManagedChannel;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientEvent;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarSessionServiceGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioDomainSocketChannel;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class GrpcSidecarSessionConnector implements SidecarSessionConnector {
    private final Path socketPath;
    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<ManagedChannel> activeChannel = new AtomicReference<>();
    private final AtomicReference<ClientCallStreamObserver<ClientEvent>> activeCall =
            new AtomicReference<>();

    GrpcSidecarSessionConnector(Path socketPath) {
        this.socketPath = socketPath;
    }

    @Override
    public void openControlSession(
            ClientHello hello,
            Consumer<SidecarEvent> eventConsumer,
            Consumer<SidecarControlSession> sessionConsumer) throws InterruptedException {
        if (closed.get()) {
            throw new IllegalStateException("Sidecar session connector is closed");
        }
        ManagedChannel channel = NettyChannelBuilder
                .forAddress(UnixDomainSocketAddress.of(socketPath))
                .channelType(NioDomainSocketChannel.class, UnixDomainSocketAddress.class)
                .eventLoopGroup(eventLoopGroup)
                .overrideAuthority("localhost")
                .usePlaintext()
                .build();
        activeChannel.set(channel);

        CountDownLatch terminated = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            StreamObserver<ClientEvent> requestStream = SidecarSessionServiceGrpc.newStub(channel)
                    .openControlSession(new ClientResponseObserver<ClientEvent, SidecarEvent>() {
                        @Override
                        public void beforeStart(ClientCallStreamObserver<ClientEvent> call) {
                            activeCall.set(call);
                        }

                        @Override
                        public void onNext(SidecarEvent event) {
                            try {
                                eventConsumer.accept(event);
                            } catch (RuntimeException exception) {
                                failure.compareAndSet(null, exception);
                                ClientCallStreamObserver<ClientEvent> requestStream = activeCall.get();
                                if (requestStream != null) {
                                    requestStream.cancel("invalid Sidecar session event", exception);
                                }
                                terminated.countDown();
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            failure.compareAndSet(null, throwable);
                            terminated.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            terminated.countDown();
                        }
                    });
            requestStream.onNext(ClientEvent.newBuilder().setHello(hello).build());
            sessionConsumer.accept(new GrpcControlSession(requestStream));
            terminated.await();
            Throwable cause = failure.get();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause != null) {
                throw new SidecarBootstrapException("Sidecar control session failed", cause);
            }
        } finally {
            activeCall.set(null);
            activeChannel.compareAndSet(channel, null);
            channel.shutdownNow();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ClientCallStreamObserver<ClientEvent> requestStream = activeCall.getAndSet(null);
        if (requestStream != null) {
            requestStream.onCompleted();
        }
        ManagedChannel channel = activeChannel.getAndSet(null);
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
        eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    private static final class GrpcControlSession implements SidecarControlSession {
        private final StreamObserver<ClientEvent> requestStream;

        private GrpcControlSession(StreamObserver<ClientEvent> requestStream) {
            this.requestStream = requestStream;
        }

        @Override
        public void register(LocalServiceRegistration registration) {
            requestStream.onNext(registration.registrationEvent());
        }

        @Override
        public void unregister(String registrationId) {
            requestStream.onNext(LocalServiceRegistration.unregistrationEvent(registrationId));
        }

        @Override
        public void close() {
            requestStream.onCompleted();
        }
    }
}
