package io.github.latticehub.adapter.spring.common;

import java.net.InetSocketAddress;

public interface SidecarHttpListenerProvider extends AutoCloseable {
    InetSocketAddress listenerAddress();

    @Override
    default void close() {
    }
}
