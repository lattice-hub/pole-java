package io.github.latticehub.agent.plugin.dubbo.v3;

import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.asm.Advice;

final class DubboProviderAdvice {
    private DubboProviderAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(1) Object invocation) {
        PoleAgentBridge.dispatch(Dubbo3xInstaller.PROVIDER_ENTER_EVENT, invocation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit() {
        PoleAgentBridge.dispatch(Dubbo3xInstaller.PROVIDER_EXIT_EVENT, null);
    }
}
