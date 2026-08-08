package io.github.latticehub.agent.plugin.thrift.http;

import io.github.latticehub.agent.api.PoleAgentContext;
import io.github.latticehub.agent.api.PoleAgentPlugin;
import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.io.IOException;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class ThriftHttpAgentPlugin implements PoleAgentPlugin {
    @Override
    public String id() {
        return "thrift-http";
    }

    @Override
    public void install(PoleAgentContext context) throws IOException {
        ThriftHttpInstaller.initialize(context);
        PoleAgentBridge.register(ThriftHttpInstaller.CLIENT_EVENT, ThriftHttpInstaller::injectClient);
        PoleAgentBridge.register(ThriftHttpInstaller.SERVER_ENTER_EVENT, ThriftHttpInstaller::enterServer);
        PoleAgentBridge.register(ThriftHttpInstaller.SERVER_EXIT_EVENT, ignored -> ThriftHttpInstaller.exitServer());
        installClient(context);
        installServer(context);
    }

    private static void installClient(PoleAgentContext context) {
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("io.github.latticehub.agent.")))
                .type(named("org.apache.thrift.transport.THttpClient"))
                .transform((builder, type, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(ThriftHttpClientAdvice.class).on(named("flush").and(takesArguments(0)))))
                .installOn(context.instrumentation());
    }

    private static void installServer(PoleAgentContext context) {
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("io.github.latticehub.agent.")))
                .type(named("org.apache.thrift.server.TServlet"))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(ThriftHttpServerAdvice.class).on(named("doPost").and(takesArguments(2)))))
                .installOn(context.instrumentation());
    }
}
