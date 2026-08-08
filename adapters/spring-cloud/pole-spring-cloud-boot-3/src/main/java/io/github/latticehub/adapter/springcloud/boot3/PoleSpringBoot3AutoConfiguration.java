package io.github.latticehub.adapter.springcloud.boot3;

import io.github.latticehub.adapter.springcloud.common.LazySidecarHttpListenerProvider;
import io.github.latticehub.adapter.springcloud.common.PoleTrafficContextTaskDecorator;
import io.github.latticehub.adapter.springcloud.common.SidecarHttpListenerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class PoleSpringBoot3AutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    SidecarHttpListenerProvider poleSidecarHttpListenerProvider() {
        return new LazySidecarHttpListenerProvider();
    }

    @Bean
    @ConditionalOnMissingBean(org.springframework.core.task.TaskDecorator.class)
    PoleTrafficContextTaskDecorator poleTrafficContextTaskDecorator() {
        return new PoleTrafficContextTaskDecorator();
    }

    @Bean
    @ConditionalOnBean(PoleTrafficContextTaskDecorator.class)
    ThreadPoolTaskExecutorCustomizer poleTrafficContextTaskExecutorCustomizer(
            PoleTrafficContextTaskDecorator decorator) {
        return executor -> executor.setTaskDecorator(decorator);
    }
}
