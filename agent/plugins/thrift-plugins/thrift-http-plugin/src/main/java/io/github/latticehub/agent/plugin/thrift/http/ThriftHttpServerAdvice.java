package io.github.latticehub.agent.plugin.thrift.http;

import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.asm.Advice;

final class ThriftHttpServerAdvice {
    private ThriftHttpServerAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) Object request) {
        PoleAgentBridge.dispatch(ThriftHttpInstaller.SERVER_ENTER_EVENT, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit() {
        PoleAgentBridge.dispatch(ThriftHttpInstaller.SERVER_EXIT_EVENT, null);
    }
}
