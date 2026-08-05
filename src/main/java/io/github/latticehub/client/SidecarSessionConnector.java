package io.github.latticehub.client;

import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import java.util.function.Consumer;

interface SidecarSessionConnector extends AutoCloseable {
    void openControlSession(
            ClientHello hello,
            Consumer<SidecarEvent> eventConsumer,
            Consumer<SidecarControlSession> sessionConsumer) throws Exception;

    @Override
    void close();
}
