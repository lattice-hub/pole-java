package io.github.latticehub.agent.plugin.springcloud;

import io.github.latticehub.agent.api.PoleAgentContext;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class SpringCloudInstaller {
    private static final String ADAPTER_PACKAGE = "io.github.latticehub.adapter.springcloud.";
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
            Class<?> initializerType = context().loadIsolatedClass(
                    "spring-cloud",
                    "boot-" + major,
                    applicationLoader,
                    initializerName,
                    List.of(ADAPTER_PACKAGE + "common", ADAPTER_PACKAGE + "boot" + major));
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

    private static PoleAgentContext context() {
        PoleAgentContext current = context;
        if (current == null) {
            throw new IllegalStateException("Spring Cloud Agent plugin has not been initialized");
        }
        return current;
    }
}
