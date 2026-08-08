package io.github.latticehub.adapter.springcloud.common;

import java.net.InetSocketAddress;

public interface SidecarHttpListenerProvider extends AutoCloseable {
    InetSocketAddress listenerAddress();

    @Override
    default void close() {
    }
}
