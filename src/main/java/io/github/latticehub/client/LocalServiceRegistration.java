package io.github.latticehub.client;

import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientEvent;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.Protocol;
import java.util.Objects;

public final class LocalServiceRegistration {
    private final String registrationId;
    private final String namespace;
    private final String service;
    private final SidecarProtocol protocol;
    private final int localPort;

    private LocalServiceRegistration(
            String registrationId,
            String namespace,
            String service,
            SidecarProtocol protocol,
            int localPort) {
        this.registrationId = requireText("registrationId", registrationId);
        this.namespace = requireText("namespace", namespace);
        this.service = requireText("service", service);
        this.protocol = Objects.requireNonNull(protocol, "protocol must not be null");
        if (localPort < 1 || localPort > 65_535) {
            throw new IllegalArgumentException("localPort must be in the range 1..65535");
        }
        this.localPort = localPort;
    }

    public static LocalServiceRegistration of(
            String registrationId,
            String namespace,
            String service,
            SidecarProtocol protocol,
            int localPort) {
        return new LocalServiceRegistration(registrationId, namespace, service, protocol, localPort);
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getService() {
        return service;
    }

    public SidecarProtocol getProtocol() {
        return protocol;
    }

    public int getLocalPort() {
        return localPort;
    }

    ClientEvent registrationEvent() {
        io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.LocalServiceRegistration registration = io.github.latticehub.pole.specification.api.v1.sidecar
                .SidecarBootstrapProto.LocalServiceRegistration.newBuilder()
                .setRegistrationId(registrationId)
                .setNamespace(namespace)
                .setService(service)
                .setProtocol(protocolValue(protocol))
                .setLocalPort(localPort)
                .build();
        return ClientEvent.newBuilder().setRegisterLocalService(registration).build();
    }

    static ClientEvent unregistrationEvent(String registrationId) {
        return ClientEvent.newBuilder()
                .setUnregisterLocalService(
                        io.github.latticehub.pole.specification.api.v1.sidecar
                                .SidecarBootstrapProto.LocalServiceUnregistration.newBuilder()
                                .setRegistrationId(requireText("registrationId", registrationId)))
                .build();
    }

    private static Protocol protocolValue(SidecarProtocol protocol) {
        return switch (protocol) {
            case HTTP -> Protocol.PROTOCOL_HTTP;
            case GRPC -> Protocol.PROTOCOL_GRPC;
            case DUBBO -> Protocol.PROTOCOL_DUBBO;
            case THRIFT -> Protocol.PROTOCOL_THRIFT;
        };
    }

    private static String requireText(String name, String value) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
