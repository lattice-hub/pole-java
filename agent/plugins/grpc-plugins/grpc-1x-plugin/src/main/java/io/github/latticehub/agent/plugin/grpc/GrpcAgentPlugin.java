package io.github.latticehub.agent.plugin.grpc;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;
import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class GrpcAgentPlugin implements PoleAgentPlugin {
    @Override
    public String id() {
        return "grpc-1x";
    }

    @Override
    public void install(PoleAgentContext context) {
        GrpcInstaller.initialize(context);
        PoleAgentBridge.register(GrpcInstaller.MANAGED_CHANNEL_BUILDER_EVENT, GrpcInstaller::installClient);
        PoleAgentBridge.register(GrpcInstaller.SERVER_BUILDER_EVENT, GrpcInstaller::installServer);
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("io.github.latticehub.agent.")))
                .type(nameStartsWith("io.grpc.")
                        .and(hasSuperType(named("io.grpc.ManagedChannelBuilder")))
                        .and(not(isAbstract())))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(GrpcManagedChannelBuilderAdvice.class)
                                .on(named("build").and(takesArguments(0)).and(returns(named("io.grpc.ManagedChannel"))))))
                .type(nameStartsWith("io.grpc.")
                        .and(hasSuperType(named("io.grpc.ServerBuilder")))
                        .and(not(isAbstract())))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(GrpcServerBuilderAdvice.class)
                                .on(named("build").and(takesArguments(0)).and(returns(named("io.grpc.Server"))))))
                .installOn(context.instrumentation());
    }
}
