package io.github.latticehub.agent.plugin.dubbo.v3;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;
import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.io.IOException;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class Dubbo3xAgentPlugin implements PoleAgentPlugin {
    @Override
    public String id() {
        return "dubbo-3x";
    }

    @Override
    public void install(PoleAgentContext context) throws IOException {
        Dubbo3xInstaller.initialize(context);
        PoleAgentBridge.register(Dubbo3xInstaller.CONSUMER_EVENT, Dubbo3xInstaller::injectConsumer);
        PoleAgentBridge.register(Dubbo3xInstaller.PROVIDER_ENTER_EVENT, Dubbo3xInstaller::enterProvider);
        PoleAgentBridge.register(Dubbo3xInstaller.PROVIDER_EXIT_EVENT, ignored -> Dubbo3xInstaller.exitProvider());
        installAdvice(
                context,
                "org.apache.dubbo.rpc.cluster.filter.support.ConsumerContextFilter",
                DubboConsumerAdvice.class);
        installAdvice(context, "org.apache.dubbo.rpc.filter.ContextFilter", DubboProviderAdvice.class);
    }

    private static void installAdvice(PoleAgentContext context, String typeName, Class<?> advice) {
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("io.github.latticehub.agent.")))
                .type(named(typeName))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(advice).on(named("invoke").and(takesArguments(2)))))
                .installOn(context.instrumentation());
    }
}
