package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.Listener;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ListenerSnapshot;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.Protocol;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidecarListenerSnapshotTest {
    @Test
    void acceptsCompleteSnapshot() {
        SidecarListenerSnapshot snapshot = SidecarListenerSnapshot.fromEvent(validEvent(15001));

        assertEquals(15001, snapshot.address(SidecarProtocol.HTTP).getPort());
        assertEquals("127.0.0.1", snapshot.address(SidecarProtocol.THRIFT).getHostString());
        assertEquals(4, snapshot.addresses().size());
    }

    @Test
    void rejectsMissingDuplicateUnspecifiedAndInvalidPorts() {
        assertThrows(SidecarBootstrapException.class, () -> SidecarListenerSnapshot.fromEvent(
                event(List.of(listener(Protocol.PROTOCOL_HTTP, 15001)))));
        assertThrows(SidecarBootstrapException.class, () -> SidecarListenerSnapshot.fromEvent(
                event(List.of(
                        listener(Protocol.PROTOCOL_HTTP, 15001),
                        listener(Protocol.PROTOCOL_HTTP, 15002),
                        listener(Protocol.PROTOCOL_GRPC, 15003),
                        listener(Protocol.PROTOCOL_DUBBO, 15004),
                        listener(Protocol.PROTOCOL_THRIFT, 15005)))));
        assertThrows(SidecarBootstrapException.class, () -> SidecarListenerSnapshot.fromEvent(
                event(List.of(
                        listener(Protocol.PROTOCOL_UNSPECIFIED, 15000),
                        listener(Protocol.PROTOCOL_HTTP, 15001),
                        listener(Protocol.PROTOCOL_GRPC, 15002),
                        listener(Protocol.PROTOCOL_DUBBO, 15003),
                        listener(Protocol.PROTOCOL_THRIFT, 15004)))));
        assertThrows(SidecarBootstrapException.class, () -> SidecarListenerSnapshot.fromEvent(
                event(List.of(
                        listener(Protocol.PROTOCOL_HTTP, 0),
                        listener(Protocol.PROTOCOL_GRPC, 15002),
                        listener(Protocol.PROTOCOL_DUBBO, 15003),
                        listener(Protocol.PROTOCOL_THRIFT, 15004)))));
    }

    static SidecarEvent validEvent(int firstPort) {
        return event(List.of(
                listener(Protocol.PROTOCOL_HTTP, firstPort),
                listener(Protocol.PROTOCOL_GRPC, firstPort + 1),
                listener(Protocol.PROTOCOL_DUBBO, firstPort + 2),
                listener(Protocol.PROTOCOL_THRIFT, firstPort + 3)));
    }

    private static Listener listener(Protocol protocol, int port) {
        return Listener.newBuilder().setProtocol(protocol).setPort(port).build();
    }

    private static SidecarEvent event(List<Listener> listeners) {
        return SidecarEvent.newBuilder()
                .setListenerSnapshot(ListenerSnapshot.newBuilder().addAllListeners(listeners))
                .build();
    }
}
