package io.github.latticehub.adapter.spring.common;

import org.springframework.core.env.Environment;

public final class PoleSpringSettings {
    public static final String NAMESPACE_PROPERTY = "pole.namespace";
    public static final String NAMESPACE_ENVIRONMENT_VARIABLE = "POD_NAMESPACE";

    private PoleSpringSettings() {
    }

    public static String namespace(Environment environment) {
        String configured = environment.getProperty(NAMESPACE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty(NAMESPACE_ENVIRONMENT_VARIABLE);
        }
        return configured == null || configured.isBlank() ? "default" : configured;
    }
}
