package io.github.latticehub.adapter.springcloud.boot2;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

public final class PoleSpringBoot2Initializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        AnnotatedBeanDefinitionReader reader =
                new AnnotatedBeanDefinitionReader((BeanDefinitionRegistry) applicationContext.getBeanFactory());
        reader.register(PoleSpringBoot2AutoConfiguration.class);
        if (isPresent("org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer",
                applicationContext.getClassLoader())
                && isPresent("org.springframework.http.HttpRequest", applicationContext.getClassLoader())) {
            reader.register(PoleSpringBoot2BlockingAutoConfiguration.class);
        }
        if (isPresent("org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer",
                applicationContext.getClassLoader())
                && isPresent("org.springframework.web.reactive.function.client.ClientRequest",
                applicationContext.getClassLoader())) {
            reader.register(PoleSpringBoot2ReactiveAutoConfiguration.class);
        }
        if (isPresent("javax.servlet.Filter", applicationContext.getClassLoader())) {
            reader.register(PoleSpringBoot2ServletAutoConfiguration.class);
        }
    }

    private static boolean isPresent(String name, ClassLoader classLoader) {
        try {
            Class.forName(name, false, classLoader);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
