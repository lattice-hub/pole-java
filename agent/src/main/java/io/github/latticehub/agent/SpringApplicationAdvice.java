package io.github.latticehub.agent;

import net.bytebuddy.asm.Advice;

final class SpringApplicationAdvice {
    private SpringApplicationAdvice() {
    }

    @Advice.OnMethodExit
    static void install(@Advice.This Object application) {
        PoleSpringInstaller.install(application);
    }
}
