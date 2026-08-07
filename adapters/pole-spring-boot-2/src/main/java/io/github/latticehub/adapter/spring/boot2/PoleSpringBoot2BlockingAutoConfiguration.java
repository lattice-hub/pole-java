package io.github.latticehub.adapter.spring.boot2;

import io.github.latticehub.adapter.spring.common.PoleBlockingLoadBalancerRequestTransformer;
import io.github.latticehub.adapter.spring.common.PoleSpringSettings;
import io.github.latticehub.adapter.spring.common.SidecarHttpListenerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer",
        "org.springframework.http.HttpRequest"
})
public class PoleSpringBoot2BlockingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    LoadBalancerRequestTransformer poleBlockingLoadBalancerRequestTransformer(
            SidecarHttpListenerProvider provider, Environment environment) {
        return new PoleBlockingLoadBalancerRequestTransformer(provider, PoleSpringSettings.namespace(environment));
    }
}
