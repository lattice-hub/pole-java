package io.github.latticehub.agent.bootstrap;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class PoleAgentBridge {
    private static final Map<String, PoleAgentCallback> CALLBACKS = new ConcurrentHashMap<>();

    private PoleAgentBridge() {
    }

    public static void register(String event, PoleAgentCallback callback) {
        String eventName = requireEvent(event);
        PoleAgentCallback existing = CALLBACKS.putIfAbsent(eventName, Objects.requireNonNull(callback, "callback"));
        if (existing != null) {
            throw new IllegalStateException("Pole Agent callback is already registered: " + eventName);
        }
    }

    public static void dispatch(String event, Object target) {
        PoleAgentCallback callback = CALLBACKS.get(requireEvent(event));
        if (callback == null) {
            throw new IllegalStateException("Pole Agent callback is not registered: " + event);
        }
        callback.accept(target);
    }

    static void clear() {
        CALLBACKS.clear();
    }

    private static String requireEvent(String event) {
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        return event;
    }
}
