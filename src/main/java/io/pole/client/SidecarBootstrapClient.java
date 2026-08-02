package io.pole.client;

import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.Protocol;
import io.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SidecarBootstrapClient implements AutoCloseable {
    private static final Duration DEFAULT_INITIALIZATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(100);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(2);

    private final Path socketPath;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final SidecarSessionConnector connector;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<SidecarListenerSnapshot> listenerSnapshot =
            new AtomicReference<>();
    private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
    private final Object snapshotMonitor = new Object();
    private final Thread sessionThread;

    private SidecarBootstrapClient(Builder builder, SidecarSessionConnector connector) {
        this.socketPath = builder.socketPath;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.connector = connector;
        this.sessionThread = new Thread(this::runSessionLoop, "pole-sidecar-session");
        this.sessionThread.setDaemon(true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SidecarBootstrapClient connect() {
        return builder().connect();
    }

    public boolean isAvailable() {
        return listenerSnapshot.get() != null;
    }

    public Path getSocketPath() {
        return socketPath;
    }

    public InetSocketAddress listenerAddress(SidecarProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        SidecarListenerSnapshot snapshot = requireSnapshot();
        return snapshot.address(protocol);
    }

    public Map<SidecarProtocol, InetSocketAddress> listenerAddresses() {
        return requireSnapshot().addresses();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        listenerSnapshot.set(null);
        signalSnapshotChange();
        connector.close();
        sessionThread.interrupt();
        if (Thread.currentThread() != sessionThread) {
            try {
                sessionThread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startAndAwait(Duration initializationTimeout) {
        sessionThread.start();
        long deadline = System.nanoTime() + initializationTimeout.toNanos();
        synchronized (snapshotMonitor) {
            while (listenerSnapshot.get() == null && !closed.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(snapshotMonitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    close();
                    throw new SidecarBootstrapException(
                            "interrupted while waiting for Sidecar bootstrap", exception);
                }
            }
        }
        if (listenerSnapshot.get() == null) {
            Throwable cause = lastFailure.get();
            close();
            throw new SidecarBootstrapException(
                    "Sidecar bootstrap did not become ready within " + initializationTimeout,
                    cause);
        }
    }

    private void runSessionLoop() {
        long backoffNanos = initialBackoff.toNanos();
        while (!closed.get()) {
            AtomicBoolean firstEvent = new AtomicBoolean(true);
            AtomicBoolean installedSnapshot = new AtomicBoolean();
            try {
                connector.openSession(clientHello(), event -> {
                    installFirstSnapshot(firstEvent, event);
                    installedSnapshot.set(true);
                });
                lastFailure.set(new SidecarBootstrapException(
                        "Sidecar completed OpenSession unexpectedly"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (!closed.get()) {
                    lastFailure.set(exception);
                }
            } catch (RuntimeException exception) {
                if (!closed.get()) {
                    lastFailure.set(exception);
                }
            } catch (Exception exception) {
                if (!closed.get()) {
                    lastFailure.set(exception);
                }
            } finally {
                listenerSnapshot.set(null);
                signalSnapshotChange();
            }

            if (closed.get()) {
                return;
            }
            if (installedSnapshot.get()) {
                backoffNanos = initialBackoff.toNanos();
            }
            try {
                TimeUnit.NANOSECONDS.sleep(backoffNanos);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffNanos = Math.min(maxBackoff.toNanos(), saturatedDouble(backoffNanos));
        }
    }

    private void installFirstSnapshot(AtomicBoolean firstEvent, SidecarEvent event) {
        if (!firstEvent.compareAndSet(true, false)) {
            throw new SidecarBootstrapException(
                    "Sidecar must send exactly one listener snapshot per session");
        }
        listenerSnapshot.set(SidecarListenerSnapshot.fromEvent(event));
        lastFailure.set(null);
        signalSnapshotChange();
    }

    private SidecarListenerSnapshot requireSnapshot() {
        SidecarListenerSnapshot snapshot = listenerSnapshot.get();
        if (snapshot == null) {
            throw new SidecarUnavailableException(
                    "Sidecar listener snapshot is unavailable; business requests must fail fast");
        }
        return snapshot;
    }

    private void signalSnapshotChange() {
        synchronized (snapshotMonitor) {
            snapshotMonitor.notifyAll();
        }
    }

    private static ClientHello clientHello() {
        String implementationVersion = SidecarBootstrapClient.class
                .getPackage()
                .getImplementationVersion();
        return ClientHello.newBuilder()
                .setSdkLanguage("java")
                .setSdkVersion(implementationVersion == null ? "development" : implementationVersion)
                .addSupportedProtocols(Protocol.PROTOCOL_HTTP)
                .addSupportedProtocols(Protocol.PROTOCOL_GRPC)
                .addSupportedProtocols(Protocol.PROTOCOL_DUBBO)
                .addSupportedProtocols(Protocol.PROTOCOL_THRIFT)
                .build();
    }

    private static long saturatedDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value * 2;
    }

    public static final class Builder {
        private Path socketPath = PoleClientDefaults.sidecarSocketPath();
        private Duration initializationTimeout = DEFAULT_INITIALIZATION_TIMEOUT;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;
        private Duration maxBackoff = DEFAULT_MAX_BACKOFF;
        private SidecarSessionConnector connector;

        private Builder() {
        }

        public Builder socketPath(Path socketPath) {
            this.socketPath = Objects.requireNonNull(socketPath, "socketPath must not be null");
            return this;
        }

        public Builder initializationTimeout(Duration initializationTimeout) {
            this.initializationTimeout = requirePositive(
                    "initializationTimeout",
                    initializationTimeout);
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = requirePositive("initialBackoff", initialBackoff);
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = requirePositive("maxBackoff", maxBackoff);
            return this;
        }

        Builder connector(SidecarSessionConnector connector) {
            this.connector = Objects.requireNonNull(connector, "connector must not be null");
            return this;
        }

        public SidecarBootstrapClient connect() {
            if (initialBackoff.compareTo(maxBackoff) > 0) {
                throw new IllegalArgumentException("initialBackoff must not exceed maxBackoff");
            }
            SidecarSessionConnector sessionConnector = connector == null
                    ? new GrpcSidecarSessionConnector(socketPath)
                    : connector;
            SidecarBootstrapClient client = new SidecarBootstrapClient(this, sessionConnector);
            client.startAndAwait(initializationTimeout);
            return client;
        }

        private static Duration requirePositive(String name, Duration duration) {
            Objects.requireNonNull(duration, name + " must not be null");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return duration;
        }
    }
}
