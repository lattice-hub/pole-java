package io.github.latticehub.adapter.springcloud.boot3;

import io.github.latticehub.adapter.springcloud.common.PoleBlockingLoadBalancerRequestTransformer;
import io.github.latticehub.adapter.springcloud.common.PoleSpringSettings;
import io.github.latticehub.adapter.springcloud.common.SidecarHttpListenerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(name = {
        "org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer",
        "org.springframework.http.HttpRequest"
})
public class PoleSpringBoot3BlockingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    LoadBalancerRequestTransformer poleBlockingLoadBalancerRequestTransformer(
            SidecarHttpListenerProvider provider, Environment environment) {
        return new PoleBlockingLoadBalancerRequestTransformer(provider, PoleSpringSettings.namespace(environment));
    }
}
