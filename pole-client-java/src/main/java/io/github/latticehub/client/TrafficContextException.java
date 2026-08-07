package io.github.latticehub.client;

import java.util.Objects;

public final class TrafficContextException extends IllegalArgumentException {
    private final TrafficContextDiagnostic diagnostic;

    public TrafficContextException(TrafficContextDiagnostic diagnostic, String message) {
        super(message);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic must not be null");
    }

    public TrafficContextException(TrafficContextDiagnostic diagnostic, String message, Throwable cause) {
        super(message, cause);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic must not be null");
    }

    public TrafficContextDiagnostic getDiagnostic() {
        return diagnostic;
    }

    public String getCode() {
        return diagnostic.name();
    }
}
