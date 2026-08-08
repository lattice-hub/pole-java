package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.api.PoleAgentContext;
import net.bytebuddy.dynamic.loading.ClassInjector;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;

public final class SpringCloudInstaller {
    static final String APPLICATION_EVENT = "spring-cloud.application";
    private static final String COMMON_PACKAGE = "io.github.latticehub.adapter.springcloud.common.";
    private static final String SPRING_CLOUD_MARKER = "org.springframework.cloud.client.ServiceInstance";
    private static final String SELECTED_PLUGIN_PROPERTY = "pole.agent.spring-cloud.selected-plugin";
    static final String PAYLOAD_PACKAGE = "io.github.latticehub.agent.plugin.springcloud.payload.";
    private static final Set<Object> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile PoleAgentContext context;
    private static volatile List<SpringCloudVersionPlugin> versionPlugins = List.of();

    private SpringCloudInstaller() {
    }

    static void initialize(PoleAgentContext agentContext) {
        context = agentContext;
        versionPlugins = ServiceLoader.load(
                        SpringCloudVersionPlugin.class, SpringCloudInstaller.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(SpringCloudVersionPlugin::id))
                .toList();
        if (versionPlugins.isEmpty()) {
            throw new IllegalStateException("Pole Java Agent found no Spring Cloud version plugins");
        }
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
            int bootMajor = majorVersion(
                    applicationType.getPackage().getImplementationVersion(), "Spring Boot");
            Integer cloudMajor = springCloudMajor(applicationLoader);
            SpringCloudVersionPlugin versionPlugin = selectVersionPlugin(versionPlugins, bootMajor, cloudMajor);
            Class<?> initializerInterface = Class.forName(
                    "org.springframework.context.ApplicationContextInitializer", false, applicationLoader);
            Class<?> initializerType = injectPayload(applicationLoader, versionPlugin);
            Object initializer = initializerType.getConstructor().newInstance();
            Object initializers = Array.newInstance(initializerInterface, 1);
            Array.set(initializers, 0, initializer);
            Method addInitializers = applicationType.getMethod("addInitializers", initializers.getClass());
            addInitializers.invoke(springApplication, initializers);
            System.setProperty(SELECTED_PLUGIN_PROPERTY, versionPlugin.id());
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install Spring Cloud adapter", exception.getCause());
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Pole Java Agent failed to install Spring Cloud adapter", exception);
        }
    }

    static int majorVersion(String version, String product) {
        if (version == null || version.isBlank()) {
            throw new IllegalStateException(product + " implementation version is unavailable");
        }
        int separator = version.indexOf('.');
        String major = separator < 0 ? version : version.substring(0, separator);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("invalid " + product + " implementation version: " + version, exception);
        }
    }

    static SpringCloudVersionPlugin selectVersionPlugin(
            List<SpringCloudVersionPlugin> candidates, int bootMajor, Integer cloudMajor) {
        List<SpringCloudVersionPlugin> matches = candidates.stream()
                .filter(candidate -> candidate.supports(bootMajor, cloudMajor))
                .toList();
        if (matches.size() != 1) {
            String cloud = cloudMajor == null ? "unavailable" : cloudMajor.toString();
            throw new IllegalStateException("Pole Java Agent requires exactly one Spring Cloud plugin for Boot "
                    + bootMajor + " and Cloud " + cloud + ", found " + matches.size());
        }
        return matches.get(0);
    }

    private static Integer springCloudMajor(ClassLoader applicationLoader) {
        try {
            Class<?> marker = Class.forName(SPRING_CLOUD_MARKER, false, applicationLoader);
            String version = marker.getPackage().getImplementationVersion();
            return version == null || version.isBlank() ? null : majorVersion(version, "Spring Cloud");
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    private static Class<?> injectPayload(ClassLoader applicationLoader, SpringCloudVersionPlugin versionPlugin)
            throws IOException, ClassNotFoundException {
        PoleAgentContext agentContext = context();
        Map<String, byte[]> classes = agentContext.pluginClassBytes(List.of(
                COMMON_PACKAGE,
                versionPlugin.adapterPackage()));
        String initializerName = versionPlugin.initializerClassName();
        if (!classes.containsKey(initializerName)) {
            throw new ClassNotFoundException("Pole Spring Cloud initializer is missing: " + initializerName);
        }
        ClassInjector injector = ClassInjector.UsingUnsafe.Factory
                .resolve(agentContext.instrumentation())
                .make(applicationLoader);
        for (String className : payloadClassOrder(applicationLoader, versionPlugin)) {
            injectClassFamily(injector, applicationLoader, classes, className);
        }
        return Class.forName(initializerName, false, applicationLoader);
    }

    private static List<String> payloadClassOrder(
            ClassLoader applicationLoader, SpringCloudVersionPlugin versionPlugin) {
        int bootMajor = versionPlugin.springBootMajor();
        String bootPackage = versionPlugin.adapterPackage() + '.';
        List<String> classes = new ArrayList<>(List.of(
                COMMON_PACKAGE + "SidecarHttpListenerProvider",
                COMMON_PACKAGE + "LazySidecarHttpListenerProvider",
                COMMON_PACKAGE + "PoleTrafficContextTaskDecorator",
                bootPackage + "PoleSpringBoot" + bootMajor + "AutoConfiguration"));
        if (isPresent("org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer", applicationLoader)
                && isPresent("org.springframework.http.HttpRequest", applicationLoader)) {
            classes.add(COMMON_PACKAGE + "SpringRequestSupport");
            classes.add(COMMON_PACKAGE + "PoleSpringSettings");
            classes.add(COMMON_PACKAGE + "PoleBlockingLoadBalancerRequestTransformer");
            classes.add(bootPackage + "PoleSpringBoot" + bootMajor + "BlockingAutoConfiguration");
        }
        if (isPresent("org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer",
                applicationLoader)
                && isPresent("org.springframework.web.reactive.function.client.ClientRequest", applicationLoader)) {
            classes.add(COMMON_PACKAGE + "SpringRequestSupport");
            classes.add(COMMON_PACKAGE + "PoleSpringSettings");
            classes.add(COMMON_PACKAGE + "PoleReactiveLoadBalancerRequestTransformer");
            classes.add(COMMON_PACKAGE + "PoleReactiveTrafficContextWebFilter");
            classes.add(bootPackage + "PoleSpringBoot" + bootMajor + "ReactiveAutoConfiguration");
        }
        String servletType = bootMajor == 2 ? "javax.servlet.Filter" : "jakarta.servlet.Filter";
        if (isPresent(servletType, applicationLoader)) {
            classes.add(bootPackage + "PoleSpringBoot" + bootMajor + "TrafficContextFilter");
            classes.add(bootPackage + "PoleSpringBoot" + bootMajor + "ServletAutoConfiguration");
        }
        classes.add(versionPlugin.initializerClassName());
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
