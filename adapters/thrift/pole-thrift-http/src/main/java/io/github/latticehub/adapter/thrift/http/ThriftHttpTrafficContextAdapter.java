package io.github.latticehub.adapter.thrift.http;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public final class ThriftHttpTrafficContextAdapter {
    private ThriftHttpTrafficContextAdapter() {
    }

    public static void injectClient(Object client) {
        Map<String, String> headers = customHeaders(client);
        String existing = header(headers, TrafficContextBaggage.HEADER);
        List<String> baggage = existing == null ? List.of() : List.of(existing);
        TrafficContextBaggage.inject(baggage, TrafficContext.current().orElse(null))
                .ifPresentOrElse(
                        value -> invoke(client, "setCustomHeader", TrafficContextBaggage.HEADER, value),
                        () -> removeHeader(headers, TrafficContextBaggage.HEADER));
    }

    public static AutoCloseable enterServer(Object request) {
        List<String> baggage = requestHeaders(request, TrafficContextBaggage.HEADER);
        return TrafficContextBaggage.extract(baggage)
                .<AutoCloseable>map(TrafficContext::attach)
                .orElseGet(TrafficContext::clear);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> customHeaders(Object client) {
        for (Class<?> type = client.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType()) && field.getName().toLowerCase().contains("customheader")) {
                    try {
                        field.setAccessible(true);
                        return (Map<String, String>) field.get(client);
                    } catch (IllegalAccessException exception) {
                        throw new IllegalStateException("cannot read Thrift HTTP custom headers", exception);
                    }
                }
            }
        }
        return null;
    }

    private static List<String> requestHeaders(Object request, String name) {
        try {
            Method method = request.getClass().getMethod("getHeaders", String.class);
            Object value = method.invoke(request, name);
            if (value instanceof Enumeration<?> enumeration) {
                List<String> headers = new ArrayList<>();
                while (enumeration.hasMoreElements()) {
                    Object header = enumeration.nextElement();
                    if (header != null) {
                        headers.add(header.toString());
                    }
                }
                return List.copyOf(headers);
            }
            return List.of();
        } catch (NoSuchMethodException exception) {
            Object value = invoke(request, "getHeader", name);
            return value == null ? List.of() : List.of(value.toString());
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("cannot read Thrift HTTP request headers", cause(exception));
        }
    }

    private static Object invoke(Object target, String methodName, Object... arguments) {
        Class<?>[] argumentTypes = java.util.Arrays.stream(arguments).map(Object::getClass).toArray(Class<?>[]::new);
        try {
            return target.getClass().getMethod(methodName, argumentTypes).invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot invoke Thrift HTTP method: " + methodName, cause(exception));
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static void removeHeader(Map<String, String> headers, String name) {
        if (headers != null) {
            headers.keySet().removeIf(key -> key.equalsIgnoreCase(name));
        }
    }

    private static Throwable cause(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : exception;
    }
}
