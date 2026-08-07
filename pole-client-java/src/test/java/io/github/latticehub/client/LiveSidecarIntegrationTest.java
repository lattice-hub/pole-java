package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LiveSidecarIntegrationTest {
    @Test
    void receivesTheCompleteSnapshotFromALiveSidecar() {
        String socketPath = System.getenv("POLE_SIDECAR_INTEGRATION_SOCKET");
        Assumptions.assumeTrue(socketPath != null && !socketPath.isBlank());

        try (SidecarBootstrapClient client = SidecarBootstrapClient.builder()
                .socketPath(Path.of(socketPath))
                .initializationTimeout(Duration.ofSeconds(3))
                .connect()) {
            assertEquals(4, client.listenerAddresses().size());
            for (SidecarProtocol protocol : SidecarProtocol.values()) {
                assertEquals("127.0.0.1", client.listenerAddress(protocol).getHostString());
            }
        }
    }
}
