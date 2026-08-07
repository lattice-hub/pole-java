package io.github.latticehub.adapter.spring.boot2;

import io.github.latticehub.adapter.spring.common.PoleBlockingLoadBalancerRequestTransformer;
import io.github.latticehub.adapter.spring.common.PoleReactiveLoadBalancerRequestTransformer;
import io.github.latticehub.adapter.spring.common.PoleReactiveTrafficContextWebFilter;
import io.github.latticehub.adapter.spring.common.PoleTrafficContextTaskDecorator;
import io.github.latticehub.adapter.spring.common.SidecarHttpListenerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoleSpringBoot2AutoConfigurationTest {
    @Test
    void installsAllAdaptersWithoutConnectingAtStartup() {
        new ApplicationContextRunner()
                .withPropertyValues("pole.namespace=commerce")
                .withUserConfiguration(
                        PoleSpringBoot2AutoConfiguration.class,
                        PoleSpringBoot2BlockingAutoConfiguration.class,
                        PoleSpringBoot2ReactiveAutoConfiguration.class,
                        PoleSpringBoot2ServletAutoConfiguration.class)
                .run(context -> {
                    assertEquals(1, context.getBeanNamesForType(SidecarHttpListenerProvider.class).length);
                    assertEquals(1, context.getBeanNamesForType(PoleBlockingLoadBalancerRequestTransformer.class).length);
                    assertEquals(1, context.getBeanNamesForType(PoleReactiveLoadBalancerRequestTransformer.class).length);
                    assertEquals(1, context.getBeanNamesForType(PoleReactiveTrafficContextWebFilter.class).length);
                    assertEquals(1, context.getBeanNamesForType(PoleTrafficContextTaskDecorator.class).length);
                    assertEquals(1, context.getBeanNamesForType(PoleSpringBoot2TrafficContextFilter.class).length);
                });
    }
}
