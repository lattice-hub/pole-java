package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.grpc.stub.StreamObserver;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarSessionServiceGrpc;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrpcUnixDomainSocketIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void receivesFirstSnapshotOverGrpcUnixDomainSocket() throws Exception {
        Path socketPath = temporaryDirectory.resolve("bootstrap.sock");
        AtomicReference<ClientHello> receivedHello = new AtomicReference<>();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Server server = NettyServerBuilder
                .forAddress(UnixDomainSocketAddress.of(socketPath))
                .channelType(NioServerDomainSocketChannel.class)
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .addService(new SidecarSessionServiceGrpc.SidecarSessionServiceImplBase() {
                    @Override
                    public void openSession(
                            ClientHello request,
                            StreamObserver<SidecarEvent> responseObserver) {
                        receivedHello.set(request);
                        responseObserver.onNext(SidecarListenerSnapshotTest.validEvent(15001));
                    }
                })
                .build()
                .start();
        try {
            try (SidecarBootstrapClient client = SidecarBootstrapClient.builder()
                    .socketPath(socketPath)
                    .initializationTimeout(Duration.ofSeconds(3))
                    .connect()) {
                assertTrue(client.isAvailable());
                assertEquals(15002, client.listenerAddress(SidecarProtocol.GRPC).getPort());
                assertEquals("java", receivedHello.get().getSdkLanguage());
                assertEquals(4, receivedHello.get().getSupportedProtocolsCount());
            }
        } finally {
            server.shutdownNow().awaitTermination();
            bossGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
            workerGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
            Files.deleteIfExists(socketPath);
        }
    }
}
