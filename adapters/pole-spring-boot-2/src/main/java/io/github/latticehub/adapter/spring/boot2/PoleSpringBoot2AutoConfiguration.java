package io.github.latticehub.adapter.spring.boot2;

import io.github.latticehub.adapter.spring.common.LazySidecarHttpListenerProvider;
import io.github.latticehub.adapter.spring.common.PoleTrafficContextTaskDecorator;
import io.github.latticehub.adapter.spring.common.SidecarHttpListenerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PoleSpringBoot2AutoConfiguration {
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
    TaskExecutorCustomizer poleTrafficContextTaskExecutorCustomizer(
            PoleTrafficContextTaskDecorator decorator) {
        return executor -> executor.setTaskDecorator(decorator);
    }
}
