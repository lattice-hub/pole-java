package io.github.latticehub.agent.plugin.thrift.http;

import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.asm.Advice;

final class ThriftHttpClientAdvice {
    private ThriftHttpClientAdvice() {
    }

    @Advice.OnMethodEnter
    static void inject(@Advice.This Object client) {
        PoleAgentBridge.dispatch(ThriftHttpInstaller.CLIENT_EVENT, client);
    }
}
