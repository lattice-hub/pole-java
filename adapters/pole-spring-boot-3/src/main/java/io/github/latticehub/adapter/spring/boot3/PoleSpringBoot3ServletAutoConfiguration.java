package io.github.latticehub.adapter.spring.boot3;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class PoleSpringBoot3ServletAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PoleSpringBoot3TrafficContextFilter poleTrafficContextFilter() {
        return new PoleSpringBoot3TrafficContextFilter();
    }
}
