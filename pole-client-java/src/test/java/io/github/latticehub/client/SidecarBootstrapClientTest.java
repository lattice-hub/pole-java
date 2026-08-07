package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.ClientHello;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.LocalServiceState;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.LocalServiceStatus;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.SidecarEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SidecarBootstrapClientTest {
    @Test
    void installsSnapshotAndExposesLoopbackAddresses() {
        HoldingConnector connector = new HoldingConnector(15001);

        try (SidecarBootstrapClient client = SidecarBootstrapClient.builder()
                .socketPath(Path.of("/tmp/test.sock"))
                .initializationTimeout(Duration.ofSeconds(1))
                .connector(connector)
                .connect()) {
            assertTrue(client.isAvailable());
            assertEquals(15001, client.listenerAddress(SidecarProtocol.HTTP).getPort());
            assertEquals("127.0.0.1", client.listenerAddress(SidecarProtocol.GRPC).getHostString());
            assertEquals(4, client.listenerAddresses().size());
            assertThrows(UnsupportedOperationException.class, () -> client.listenerAddresses().clear());
            assertEquals("java", connector.hello.getSdkLanguage());
            assertEquals(4, connector.hello.getSupportedProtocolsCount());
        }
    }

    @Test
    void sendsRegistrationsProcessesStatusAndUnregisters() {
        HoldingConnector connector = new HoldingConnector(15001);
        try (SidecarBootstrapClient client = SidecarBootstrapClient.builder()
                .socketPath(Path.of("/tmp/test.sock"))
                .initializationTimeout(Duration.ofSeconds(1))
                .connector(connector)
                .connect()) {
            LocalServiceRegistration registration = client.registerLocalService(
                    "payments-http", "prod", "payments", SidecarProtocol.HTTP, 8080);
            assertEquals(List.of(registration.getRegistrationId()), connector.registrations);
            connector.emitStatus(registration.getRegistrationId(), LocalServiceState.LOCAL_SERVICE_STATE_REGISTERED,
                    "accepted");
            LocalServiceRegistrationStatus status = client.localServiceStatus(registration.getRegistrationId())
                    .orElseThrow();
            assertEquals(LocalServiceRegistrationState.REGISTERED, status.getState());
            assertEquals("accepted", status.getMessage());
            assertTrue(client.unregisterLocalService(registration.getRegistrationId()));
            assertEquals(List.of(registration.getRegistrationId()), connector.unregistrations);
            assertFalse(client.unregisterLocalService(registration.getRegistrationId()));
        }
    }

    @Test
    void invalidatesOldSnapshotAndReplaysRegistrationsOnReconnect() throws Exception {
        ReconnectingConnector connector = new ReconnectingConnector();

        try (SidecarBootstrapClient client = SidecarBootstrapClient.builder()
                .socketPath(Path.of("/tmp/test.sock"))
                .initializationTimeout(Duration.ofSeconds(1))
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(100))
                .connector(connector)
                .connect()) {
            LocalServiceRegistration registration = client.registerLocalService(
                    "catalog-grpc", "prod", "catalog", SidecarProtocol.GRPC, 9090);
            assertEquals(15001, client.listenerAddress(SidecarProtocol.HTTP).getPort());
            connector.disconnectFirstSession();
            awaitCondition(() -> !client.isAvailable(), Duration.ofSeconds(1));
            assertThrows(SidecarUnavailableException.class,
                    () -> client.listenerAddress(SidecarProtocol.HTTP));
            awaitCondition(() -> client.isAvailable()
                    && client.listenerAddress(SidecarProtocol.HTTP).getPort() == 25001,
                    Duration.ofSeconds(2));
            assertEquals(List.of(registration.getRegistrationId()), connector.secondSessionRegistrations());
        }
    }

    @Test
    void retriesWithBoundedBackoffAndFailsInitializationAtDeadline() {
        FailingConnector connector = new FailingConnector();
        long started = System.nanoTime();

        SidecarBootstrapException exception = assertThrows(SidecarBootstrapException.class,
                () -> SidecarBootstrapClient.builder()
                        .socketPath(Path.of("/tmp/missing.sock"))
                        .initializationTimeout(Duration.ofMillis(120))
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(20))
                        .connector(connector)
                        .connect());

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertTrue(connector.attempts.get() >= 3);
        assertTrue(elapsed.compareTo(Duration.ofSeconds(1)) < 0);
        assertTrue(exception.getMessage().contains("did not become ready"));
        assertTrue(connector.closed);
    }

    @Test
    void closeInvalidatesSnapshotAndRejectsNewRegistrations() {
        HoldingConnector connector = new HoldingConnector(15001);
        SidecarBootstrapClient client = SidecarBootstrapClient.builder()
                .socketPath(Path.of("/tmp/test.sock"))
                .initializationTimeout(Duration.ofSeconds(1))
                .connector(connector)
                .connect();

        client.close();

        assertFalse(client.isAvailable());
        assertThrows(SidecarUnavailableException.class,
                () -> client.listenerAddress(SidecarProtocol.HTTP));
        assertThrows(IllegalStateException.class,
                () -> client.registerLocalService("prod", "payments", SidecarProtocol.HTTP, 8080));
    }

    @Test
    void validatesRegistrationAndBuilderInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> LocalServiceRegistration.of("id", "prod", "payments", SidecarProtocol.HTTP, 0));
        assertThrows(IllegalArgumentException.class,
                () -> LocalServiceRegistration.of("", "prod", "payments", SidecarProtocol.HTTP, 8080));
        assertThrows(IllegalArgumentException.class,
                () -> SidecarBootstrapClient.builder().initializationTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SidecarBootstrapClient.builder()
                .initialBackoff(Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(1))
                .connector(new FailingConnector())
                .connect());
    }

    private static void awaitCondition(CheckedCondition condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(5);
        }
        assertTrue(condition.test(), "condition did not become true within " + timeout);
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean test() throws Exception;
    }

    private static final class HoldingConnector implements SidecarSessionConnector {
        private final int firstPort;
        private final CountDownLatch closed = new CountDownLatch(1);
        private final List<String> registrations = new CopyOnWriteArrayList<>();
        private final List<String> unregistrations = new CopyOnWriteArrayList<>();
        private volatile ClientHello hello;
        private volatile Consumer<SidecarEvent> eventConsumer;

        private HoldingConnector(int firstPort) {
            this.firstPort = firstPort;
        }

        @Override
        public void openControlSession(
                ClientHello hello,
                Consumer<SidecarEvent> eventConsumer,
                Consumer<SidecarControlSession> sessionConsumer) throws InterruptedException {
            this.hello = hello;
            this.eventConsumer = eventConsumer;
            sessionConsumer.accept(new RecordingControlSession(registrations, unregistrations));
            eventConsumer.accept(SidecarListenerSnapshotTest.validEvent(firstPort));
            closed.await();
        }

        void emitStatus(String registrationId, LocalServiceState state, String message) {
            eventConsumer.accept(SidecarEvent.newBuilder().setLocalServiceStatus(
                    LocalServiceStatus.newBuilder()
                            .setRegistrationId(registrationId)
                            .setState(state)
                            .setMessage(message))
                    .build());
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    private static final class ReconnectingConnector implements SidecarSessionConnector {
        private final AtomicInteger sessions = new AtomicInteger();
        private final CountDownLatch disconnectFirst = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final List<List<String>> sessionRegistrations = new CopyOnWriteArrayList<>();

        @Override
        public void openControlSession(
                ClientHello hello,
                Consumer<SidecarEvent> eventConsumer,
                Consumer<SidecarControlSession> sessionConsumer) throws InterruptedException {
            int session = sessions.incrementAndGet();
            List<String> registrations = new CopyOnWriteArrayList<>();
            sessionRegistrations.add(registrations);
            sessionConsumer.accept(new RecordingControlSession(registrations, new CopyOnWriteArrayList<>()));
            eventConsumer.accept(SidecarListenerSnapshotTest.validEvent(session == 1 ? 15001 : 25001));
            if (session == 1) {
                disconnectFirst.await();
                return;
            }
            closed.await();
        }

        void disconnectFirstSession() {
            disconnectFirst.countDown();
        }

        List<String> secondSessionRegistrations() {
            return sessionRegistrations.get(1);
        }

        @Override
        public void close() {
            disconnectFirst.countDown();
            closed.countDown();
        }
    }

    private static final class RecordingControlSession implements SidecarControlSession {
        private final List<String> registrations;
        private final List<String> unregistrations;

        private RecordingControlSession(List<String> registrations, List<String> unregistrations) {
            this.registrations = registrations;
            this.unregistrations = unregistrations;
        }

        @Override
        public void register(LocalServiceRegistration registration) {
            registrations.add(registration.getRegistrationId());
        }

        @Override
        public void unregister(String registrationId) {
            unregistrations.add(registrationId);
        }

        @Override
        public void close() {
        }
    }

    private static final class FailingConnector implements SidecarSessionConnector {
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile boolean closed;

        @Override
        public void openControlSession(
                ClientHello hello,
                Consumer<SidecarEvent> eventConsumer,
                Consumer<SidecarControlSession> sessionConsumer) {
            attempts.incrementAndGet();
            throw new SidecarBootstrapException("UDS unavailable");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
