package io.github.latticehub.adapter.springcloud.boot2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "javax.servlet.Filter")
public class PoleSpringBoot2ServletAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PoleSpringBoot2TrafficContextFilter poleTrafficContextFilter() {
        return new PoleSpringBoot2TrafficContextFilter();
    }
}
