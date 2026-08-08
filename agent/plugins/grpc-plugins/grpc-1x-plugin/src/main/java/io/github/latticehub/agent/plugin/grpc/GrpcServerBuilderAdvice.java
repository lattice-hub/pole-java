package io.github.latticehub.agent.plugin.grpc;

import io.github.latticehub.agent.bootstrap.PoleAgentBridge;
import net.bytebuddy.asm.Advice;

final class GrpcServerBuilderAdvice {
    private GrpcServerBuilderAdvice() {
    }

    @Advice.OnMethodEnter
    static void install(@Advice.This Object builder) {
        PoleAgentBridge.dispatch("grpc.server-builder", builder);
    }
}
