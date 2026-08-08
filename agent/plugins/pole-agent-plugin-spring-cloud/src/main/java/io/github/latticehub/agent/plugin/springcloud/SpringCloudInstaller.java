package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.api.PoleAgentContext;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.bytebuddy.dynamic.loading.ClassInjector;

public final class SpringCloudInstaller {
    static final String APPLICATION_EVENT = "spring-cloud.application";
    private static final String ADAPTER_PACKAGE = "io.github.latticehub.adapter.springcloud.";
    private static final String COMMON_PACKAGE = ADAPTER_PACKAGE + "common.";
    static final String PAYLOAD_PACKAGE = "io.github.latticehub.agent.plugin.springcloud.payload.";
    private static final Map<Integer, String> INITIALIZERS = Map.of(
            2, ADAPTER_PACKAGE + "boot2.PoleSpringBoot2Initializer",
            3, ADAPTER_PACKAGE + "boot3.PoleSpringBoot3Initializer",
            4, ADAPTER_PACKAGE + "boot4.PoleSpringBoot4Initializer");
    private static final Set<Object> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile PoleAgentContext context;

    private SpringCloudInstaller() {
    }

    static void initialize(PoleAgentContext agentContext) {
        context = agentContext;
    }

    public static void install(Object springApplication) {
        synchronized (INSTALLED) {
            if (!INSTALLED.add(springApplication)) {
                return;
            }
        }
        try {
            Class<?> applicationType = springApplication.getClass();
            ClassLoader applicationLoader = applicationType.getClassLoader();
            int major = bootMajor(applicationType.getPackage().getImplementationVersion());
            String initializerName = INITIALIZERS.get(major);
            if (initializerName == null) {
                throw new IllegalStateException("Pole Java Agent supports Spring Boot 2, 3, and 4 only");
            }
            Class<?> initializerInterface = Class.forName(
                    "org.springframework.context.ApplicationContextInitializer", false, applicationLoader);
            Class<?> initializerType = injectPayload(applicationLoader, major, initializerName);
            Object initializer = initializerType.getConstructor().newInstance();
            Object initializers = Array.newInstance(initializerInterface, 1);
            Array.set(initializers, 0, initializer);
            Method addInitializers = applicationType.getMethod("addInitializers", initializers.getClass());
            addInitializers.invoke(springApplication, initializers);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install Spring Cloud adapter", exception.getCause());
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install Spring Cloud adapter", exception);
        }
    }

    static int bootMajor(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Spring Boot implementation version is unavailable");
        }
        int separator = version.indexOf('.');
        String major = separator < 0 ? version : version.substring(0, separator);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("invalid Spring Boot implementation version: " + version, exception);
        }
    }

    private static Class<?> injectPayload(ClassLoader applicationLoader, int major, String initializerName)
            throws IOException, ClassNotFoundException {
        PoleAgentContext agentContext = context();
        Map<String, byte[]> classes = agentContext.pluginClassBytes(List.of(
                ADAPTER_PACKAGE + "common",
                ADAPTER_PACKAGE + "boot" + major));
        if (!classes.containsKey(initializerName)) {
            throw new ClassNotFoundException("Pole Spring Cloud initializer is missing: " + initializerName);
        }
        ClassInjector injector = ClassInjector.UsingUnsafe.Factory
                .resolve(agentContext.instrumentation())
                .make(applicationLoader);
        for (String className : payloadClassOrder(applicationLoader, major)) {
            injectClassFamily(injector, applicationLoader, classes, className);
        }
        return Class.forName(initializerName, false, applicationLoader);
    }

    private static List<String> payloadClassOrder(ClassLoader applicationLoader, int major) {
        String bootPackage = ADAPTER_PACKAGE + "boot" + major + ".";
        List<String> classes = new ArrayList<>(List.of(
                COMMON_PACKAGE + "SidecarHttpListenerProvider",
                COMMON_PACKAGE + "LazySidecarHttpListenerProvider",
                COMMON_PACKAGE + "PoleTrafficContextTaskDecorator",
                bootPackage + "PoleSpringBoot" + major + "AutoConfiguration"));
        if (isPresent("org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer", applicationLoader)
                && isPresent("org.springframework.http.HttpRequest", applicationLoader)) {
            classes.add(COMMON_PACKAGE + "SpringRequestSupport");
            classes.add(COMMON_PACKAGE + "PoleSpringSettings");
            classes.add(COMMON_PACKAGE + "PoleBlockingLoadBalancerRequestTransformer");
            classes.add(bootPackage + "PoleSpringBoot" + major + "BlockingAutoConfiguration");
        }
        if (isPresent("org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer",
                applicationLoader)
                && isPresent("org.springframework.web.reactive.function.client.ClientRequest", applicationLoader)) {
            classes.add(COMMON_PACKAGE + "SpringRequestSupport");
            classes.add(COMMON_PACKAGE + "PoleSpringSettings");
            classes.add(COMMON_PACKAGE + "PoleReactiveLoadBalancerRequestTransformer");
            classes.add(COMMON_PACKAGE + "PoleReactiveTrafficContextWebFilter");
            classes.add(bootPackage + "PoleSpringBoot" + major + "ReactiveAutoConfiguration");
        }
        String servletType = major == 2 ? "javax.servlet.Filter" : "jakarta.servlet.Filter";
        if (isPresent(servletType, applicationLoader)) {
            classes.add(bootPackage + "PoleSpringBoot" + major + "TrafficContextFilter");
            classes.add(bootPackage + "PoleSpringBoot" + major + "ServletAutoConfiguration");
        }
        classes.add(INITIALIZERS.get(major));
        return List.copyOf(new LinkedHashSet<>(classes));
    }

    private static void injectClassFamily(
            ClassInjector injector,
            ClassLoader applicationLoader,
            Map<String, byte[]> availableClasses,
            String className) throws ClassNotFoundException {
        if (isPresent(className, applicationLoader)) {
            return;
        }
        Map<String, byte[]> family = availableClasses.entrySet().stream()
                .filter(entry -> entry.getKey().equals(className) || entry.getKey().startsWith(className + '$'))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        if (family.isEmpty()) {
            throw new ClassNotFoundException("Pole Spring Cloud payload class is missing: " + className);
        }
        injector.injectRaw(family);
    }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static PoleAgentContext context() {
        PoleAgentContext current = context;
        if (current == null) {
            throw new IllegalStateException("Spring Cloud Agent plugin has not been initialized");
        }
        return current;
    }
}
