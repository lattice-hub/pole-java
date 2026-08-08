package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public final class SpringCloudAgentPlugin implements PoleAgentPlugin {
    @Override
    public String id() {
        return "spring-cloud";
    }

    @Override
    public void install(PoleAgentContext context) {
        SpringCloudInstaller.initialize(context);
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("io.github.latticehub.agent.")))
                .type(named("org.springframework.boot.SpringApplication"))
                .transform((builder, type, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(SpringApplicationAdvice.class).on(isConstructor())))
                .installOn(context.instrumentation());
    }
}
