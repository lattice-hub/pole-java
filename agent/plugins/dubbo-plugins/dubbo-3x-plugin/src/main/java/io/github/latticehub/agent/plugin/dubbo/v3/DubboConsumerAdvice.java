package io.github.latticehub.agent.plugin.dubbo.v3;

import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.asm.Advice;

final class DubboConsumerAdvice {
    private DubboConsumerAdvice() {
    }

    @Advice.OnMethodEnter
    static void inject(@Advice.Argument(1) Object invocation) {
        PoleAgentBridge.dispatch(Dubbo3xInstaller.CONSUMER_EVENT, invocation);
    }
}
