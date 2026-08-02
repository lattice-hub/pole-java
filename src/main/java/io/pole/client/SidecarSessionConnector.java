package io.pole.client;

import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import java.util.function.Consumer;

interface SidecarSessionConnector extends AutoCloseable {
    void openSession(ClientHello hello, Consumer<SidecarEvent> eventConsumer) throws Exception;

    @Override
    void close();
}
