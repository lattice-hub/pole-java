package io.pole.client;

public final class SidecarBootstrapException extends IllegalStateException {
    public SidecarBootstrapException(String message) {
        super(message);
    }

    public SidecarBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
