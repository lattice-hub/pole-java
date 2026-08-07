package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PoleClientDefaultsTest {
    @Test
    void resolvesDefaultAndEnvironmentSocketPaths() {
        assertEquals(
                Path.of("/var/run/pole/sidecar/bootstrap.sock"),
                PoleClientDefaults.sidecarSocketPath(Map.of()));
        assertEquals(
                Path.of("/tmp/pole.sock"),
                PoleClientDefaults.sidecarSocketPath(Map.of(
                        PoleClientDefaults.SIDECAR_SOCKET_ENVIRONMENT_VARIABLE,
                        "/tmp/pole.sock")));
        assertEquals(
                PoleClientDefaults.SIDECAR_SOCKET_PATH,
                PoleClientDefaults.sidecarSocketPath(Map.of(
                        PoleClientDefaults.SIDECAR_SOCKET_ENVIRONMENT_VARIABLE,
                        " ")));
    }
}
