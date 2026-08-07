package io.github.latticehub.client;

import java.nio.file.Path;
import java.util.Map;

public final class PoleClientDefaults {
    public static final String SIDECAR_SOCKET_ENVIRONMENT_VARIABLE = "POLE_SIDECAR_SOCKET";
    public static final Path SIDECAR_SOCKET_PATH = Path.of("/var/run/pole/sidecar/bootstrap.sock");

    private PoleClientDefaults() {
    }

    public static Path sidecarSocketPath() {
        return sidecarSocketPath(System.getenv());
    }

    static Path sidecarSocketPath(Map<String, String> environment) {
        String configuredPath = environment.get(SIDECAR_SOCKET_ENVIRONMENT_VARIABLE);
        if (configuredPath == null || configuredPath.isBlank()) {
            return SIDECAR_SOCKET_PATH;
        }
        return Path.of(configuredPath);
    }
}
