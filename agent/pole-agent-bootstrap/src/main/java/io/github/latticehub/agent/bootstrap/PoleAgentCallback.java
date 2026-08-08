package io.github.latticehub.agent.bootstrap;

@FunctionalInterface
public interface PoleAgentCallback {
    void accept(Object target);
}
