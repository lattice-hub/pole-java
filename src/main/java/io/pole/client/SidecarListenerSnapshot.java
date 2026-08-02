package io.pole.client;

import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.Listener;
import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

final class SidecarListenerSnapshot {
    private static final String LOOPBACK_ADDRESS = "127.0.0.1";

    private final Map<SidecarProtocol, InetSocketAddress> addresses;

    private SidecarListenerSnapshot(Map<SidecarProtocol, InetSocketAddress> addresses) {
        this.addresses = Collections.unmodifiableMap(new EnumMap<>(addresses));
    }

    static SidecarListenerSnapshot fromEvent(SidecarEvent event) {
        if (!event.hasListenerSnapshot()) {
            throw new SidecarBootstrapException(
                    "the first Sidecar event must contain a listener snapshot");
        }

        EnumMap<SidecarProtocol, InetSocketAddress> addresses =
                new EnumMap<>(SidecarProtocol.class);
        for (Listener listener : event.getListenerSnapshot().getListenersList()) {
            SidecarProtocol protocol = switch (listener.getProtocol()) {
                case PROTOCOL_HTTP -> SidecarProtocol.HTTP;
                case PROTOCOL_GRPC -> SidecarProtocol.GRPC;
                case PROTOCOL_DUBBO -> SidecarProtocol.DUBBO;
                case PROTOCOL_THRIFT -> SidecarProtocol.THRIFT;
                case PROTOCOL_UNSPECIFIED, UNRECOGNIZED -> throw new SidecarBootstrapException(
                        "listener protocol must be one of HTTP, gRPC, Dubbo, or Thrift");
            };
            int port = listener.getPort();
            if (port < 1 || port > 65_535) {
                throw new SidecarBootstrapException(
                        "listener port for " + protocol + " must be in the range 1..65535");
            }
            InetSocketAddress previous = addresses.put(
                    protocol,
                    new InetSocketAddress(LOOPBACK_ADDRESS, port));
            if (previous != null) {
                throw new SidecarBootstrapException(
                        "listener snapshot contains duplicate protocol " + protocol);
            }
        }

        if (addresses.size() != SidecarProtocol.values().length) {
            throw new SidecarBootstrapException(
                    "listener snapshot must contain HTTP, gRPC, Dubbo, and Thrift");
        }
        return new SidecarListenerSnapshot(addresses);
    }

    InetSocketAddress address(SidecarProtocol protocol) {
        return addresses.get(protocol);
    }

    Map<SidecarProtocol, InetSocketAddress> addresses() {
        return addresses;
    }
}
