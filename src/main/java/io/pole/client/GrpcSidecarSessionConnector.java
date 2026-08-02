package io.pole.client;

import io.grpc.ManagedChannel;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import io.pole.specification.api.v1.sidecar.SidecarSessionServiceGrpc;
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
    private final AtomicReference<ClientCallStreamObserver<ClientHello>> activeCall =
            new AtomicReference<>();

    GrpcSidecarSessionConnector(Path socketPath) {
        this.socketPath = socketPath;
    }

    @Override
    public void openSession(
            ClientHello hello,
            Consumer<SidecarEvent> eventConsumer) throws InterruptedException {
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
            SidecarSessionServiceGrpc.newStub(channel).openSession(
                    hello,
                    new ClientResponseObserver<ClientHello, SidecarEvent>() {
                        @Override
                        public void beforeStart(
                                ClientCallStreamObserver<ClientHello> requestStream) {
                            activeCall.set(requestStream);
                        }

                        @Override
                        public void onNext(SidecarEvent event) {
                            try {
                                eventConsumer.accept(event);
                            } catch (RuntimeException exception) {
                                failure.compareAndSet(null, exception);
                                ClientCallStreamObserver<ClientHello> requestStream = activeCall.get();
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
            terminated.await();
            Throwable cause = failure.get();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause != null) {
                throw new SidecarBootstrapException("Sidecar session failed", cause);
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
        ClientCallStreamObserver<ClientHello> requestStream = activeCall.getAndSet(null);
        if (requestStream != null) {
            requestStream.cancel("Thin SDK is closing", null);
        }
        ManagedChannel channel = activeChannel.getAndSet(null);
        if (channel != null) {
            channel.shutdownNow();
        }
        eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }
}
