package io.github.latticehub.agent.api;

public interface PoleAgentPlugin {
    String id();

    void install(PoleAgentContext context) throws Exception;
}
