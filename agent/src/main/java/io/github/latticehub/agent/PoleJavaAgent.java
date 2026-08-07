package io.github.latticehub.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public final class PoleJavaAgent {
    private PoleJavaAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        install(instrumentation);
    }

    private static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("io.github.latticehub.agent.")))
                .type(named("org.springframework.boot.SpringApplication"))
                .transform((builder, type, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(SpringApplicationAdvice.class).on(isConstructor())))
                .installOn(instrumentation);
    }
}
