package io.github.latticehub.adapter.spring.boot4;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class PoleSpringBoot4ServletAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PoleSpringBoot4TrafficContextFilter poleTrafficContextFilter() {
        return new PoleSpringBoot4TrafficContextFilter();
    }
}
