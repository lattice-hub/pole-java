package io.github.latticehub.client;

interface SidecarControlSession {
    void register(LocalServiceRegistration registration);

    void unregister(String registrationId);

    void close();
}
