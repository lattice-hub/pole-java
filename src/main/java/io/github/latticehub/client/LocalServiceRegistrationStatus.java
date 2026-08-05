package io.github.latticehub.client;

import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.LocalServiceState;
import io.github.latticehub.pole.specification.api.v1.sidecar.SidecarBootstrapProto.LocalServiceStatus;
import java.util.Objects;

public final class LocalServiceRegistrationStatus {
    private final String registrationId;
    private final LocalServiceRegistrationState state;
    private final String message;

    private LocalServiceRegistrationStatus(
            String registrationId,
            LocalServiceRegistrationState state,
            String message) {
        this.registrationId = registrationId;
        this.state = state;
        this.message = message;
    }

    static LocalServiceRegistrationStatus fromProto(LocalServiceStatus status) {
        if (status.getRegistrationId().isBlank()) {
            throw new SidecarBootstrapException("local service status registration_id must not be blank");
        }
        LocalServiceRegistrationState state = switch (status.getState()) {
            case LOCAL_SERVICE_STATE_REGISTERED -> LocalServiceRegistrationState.REGISTERED;
            case LOCAL_SERVICE_STATE_UNREGISTERED -> LocalServiceRegistrationState.UNREGISTERED;
            case LOCAL_SERVICE_STATE_REJECTED -> LocalServiceRegistrationState.REJECTED;
            case LOCAL_SERVICE_STATE_UNSPECIFIED, UNRECOGNIZED -> throw new SidecarBootstrapException(
                    "local service status state must be registered, unregistered, or rejected");
        };
        return new LocalServiceRegistrationStatus(status.getRegistrationId(), state, status.getMessage());
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public LocalServiceRegistrationState getState() {
        return state;
    }

    public String getMessage() {
        return message;
    }
}
