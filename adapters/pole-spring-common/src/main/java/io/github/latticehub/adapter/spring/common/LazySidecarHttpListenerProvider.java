package io.github.latticehub.adapter.spring.common;

import io.github.latticehub.client.SidecarBootstrapClient;
import io.github.latticehub.client.SidecarProtocol;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Supplier;

public final class LazySidecarHttpListenerProvider implements SidecarHttpListenerProvider {
    private final Supplier<SidecarBootstrapClient> clientFactory;
    private volatile SidecarBootstrapClient client;

    public LazySidecarHttpListenerProvider() {
        this(SidecarBootstrapClient::connect);
    }

    LazySidecarHttpListenerProvider(Supplier<SidecarBootstrapClient> clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
    }

    @Override
    public InetSocketAddress listenerAddress() {
        return client().listenerAddress(SidecarProtocol.HTTP);
    }

    @Override
    public void close() {
        SidecarBootstrapClient current = client;
        if (current != null) {
            current.close();
        }
    }

    private SidecarBootstrapClient client() {
        SidecarBootstrapClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = clientFactory.get();
            }
            return client;
        }
    }
}
