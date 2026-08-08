package io.github.latticehub.agent;

import java.lang.instrument.Instrumentation;

public final class PoleJavaAgent {
    private PoleJavaAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        io.github.latticehub.agent.core.PoleJavaAgent.premain(arguments, instrumentation);
    }
}
