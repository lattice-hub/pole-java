package io.github.latticehub.adapter.spring.common;

import io.github.latticehub.client.TrafficContext;
import org.springframework.core.task.TaskDecorator;

public final class PoleTrafficContextTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        return TrafficContext.wrap(runnable);
    }
}
