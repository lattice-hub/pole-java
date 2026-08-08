package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.asm.Advice;

final class SpringApplicationAdvice {
    private SpringApplicationAdvice() {
    }

    @Advice.OnMethodExit
    static void install(@Advice.This Object application) {
        PoleAgentBridge.dispatch(SpringCloudInstaller.APPLICATION_EVENT, application);
    }
}
