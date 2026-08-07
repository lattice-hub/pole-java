package io.github.latticehub.adapter.spring.boot4;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;

public final class PoleSpringBoot4Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        AnnotatedBeanDefinitionReader reader =
                new AnnotatedBeanDefinitionReader((BeanDefinitionRegistry) applicationContext.getBeanFactory());
        reader.register(PoleSpringBoot4AutoConfiguration.class);
        if (isPresent("org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer",
                applicationContext.getClassLoader())
                && isPresent("org.springframework.http.HttpRequest", applicationContext.getClassLoader())) {
            reader.register(PoleSpringBoot4BlockingAutoConfiguration.class);
        }
        if (isPresent("org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer",
                applicationContext.getClassLoader())
                && isPresent("org.springframework.web.reactive.function.client.ClientRequest",
                applicationContext.getClassLoader())) {
            reader.register(PoleSpringBoot4ReactiveAutoConfiguration.class);
        }
        if (isPresent("jakarta.servlet.Filter", applicationContext.getClassLoader())) {
            reader.register(PoleSpringBoot4ServletAutoConfiguration.class);
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
