package io.github.latticehub.adapter.springcloud.boot4;

import io.github.latticehub.adapter.springcloud.common.PoleReactiveLoadBalancerRequestTransformer;
import io.github.latticehub.adapter.springcloud.common.PoleReactiveTrafficContextWebFilter;
import io.github.latticehub.adapter.springcloud.common.PoleSpringSettings;
import io.github.latticehub.adapter.springcloud.common.SidecarHttpListenerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(name = {
        "org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer",
        "org.springframework.web.reactive.function.client.ClientRequest"
})
public class PoleSpringBoot4ReactiveAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    LoadBalancerClientRequestTransformer poleReactiveLoadBalancerRequestTransformer(
            SidecarHttpListenerProvider provider, Environment environment) {
        return new PoleReactiveLoadBalancerRequestTransformer(provider, PoleSpringSettings.namespace(environment));
    }

    @Bean
    @ConditionalOnMissingBean
    PoleReactiveTrafficContextWebFilter poleReactiveTrafficContextWebFilter() {
        return new PoleReactiveTrafficContextWebFilter();
    }
}
