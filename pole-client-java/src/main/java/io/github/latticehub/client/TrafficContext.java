package io.github.latticehub.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public final class TrafficContext {
    public static final int VERSION = 1;

    private static volatile Storage storage = new NativeStorage();

    private final String campaign;
    private final String lane;
    private final Integer bucket;

    private TrafficContext(Builder builder) {
        this.campaign = TrafficContextBaggage.validateLabel("campaign", builder.campaign);
        this.lane = TrafficContextBaggage.validateLabel("lane", builder.lane);
        this.bucket = builder.bucket;
        if (bucket != null && (bucket < 0 || bucket > 9999)) {
            throw new TrafficContextException(
                    TrafficContextDiagnostic.INVALID_BUCKET,
                    "bucket must be between 0 and 9999");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Optional<TrafficContext> current() {
        return storage.current();
    }

    public static Scope attach(TrafficContext context) {
        return storage.attach(Objects.requireNonNull(context, "context must not be null"));
    }

    public static Scope clear() {
        return storage.attach(null);
    }

    public static boolean tryInstallOpenTelemetryBridge() {
        try {
            storage = new OpenTelemetryStorage();
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    public static void reset() {
        storage = new NativeStorage();
    }

    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        TrafficContext captured = current().orElse(null);
        return () -> {
            try (Scope ignored = attachCaptured(captured)) {
                task.run();
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        TrafficContext captured = current().orElse(null);
        return () -> {
            try (Scope ignored = attachCaptured(captured)) {
                return task.call();
            }
        };
    }

    public static Executor wrap(Executor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        return task -> executor.execute(wrap(task));
    }

    private static Scope attachCaptured(TrafficContext context) {
        return storage.attach(context);
    }

    public Optional<String> getCampaign() {
        return Optional.ofNullable(campaign);
    }

    public Optional<String> getLane() {
        return Optional.ofNullable(lane);
    }

    public Optional<Integer> getBucket() {
        return Optional.ofNullable(bucket);
    }

    public boolean hasLabels() {
        return campaign != null || lane != null || bucket != null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TrafficContext that)) {
            return false;
        }
        return Objects.equals(campaign, that.campaign)
                && Objects.equals(lane, that.lane)
                && Objects.equals(bucket, that.bucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campaign, lane, bucket);
    }

    public static final class Builder {
        private String campaign;
        private String lane;
        private Integer bucket;

        private Builder() {
        }

        public Builder campaign(String campaign) {
            this.campaign = campaign;
            return this;
        }

        public Builder lane(String lane) {
            this.lane = lane;
            return this;
        }

        public Builder bucket(Integer bucket) {
            this.bucket = bucket;
            return this;
        }

        public TrafficContext build() {
            return new TrafficContext(this);
        }
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private interface Storage {
        Optional<TrafficContext> current();

        Scope attach(TrafficContext context);
    }

    private static final class NativeStorage implements Storage {
        private final ThreadLocal<TrafficContext> current = new ThreadLocal<>();

        @Override
        public Optional<TrafficContext> current() {
            return Optional.ofNullable(current.get());
        }

        @Override
        public Scope attach(TrafficContext context) {
            TrafficContext previous = current.get();
            current.set(context);
            return new Scope() {
                private boolean closed;

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        if (previous == null) {
                            current.remove();
                        } else {
                            current.set(previous);
                        }
                    }
                }
            };
        }
    }

    private static final class OpenTelemetryStorage implements Storage {
        private static final Object CLEARED_CONTEXT = new Object();

        private final Object contextKey;
        private final Method currentMethod;
        private final Method getMethod;
        private final Method withMethod;
        private final Method makeCurrentMethod;
        private final Method closeMethod;
        private final Method baggageCurrentMethod;
        private final Method baggageToBuilderMethod;
        private final Method baggageAsMapMethod;
        private final Method baggageGetEntryValueMethod;
        private final Method baggageStoreInContextMethod;
        private final Method baggageBuilderRemoveMethod;
        private final Method baggageBuilderPutMethod;
        private final Method baggageBuilderBuildMethod;

        private OpenTelemetryStorage() throws ReflectiveOperationException {
            ClassLoader loader = TrafficContext.class.getClassLoader();
            Class<?> contextClass = Class.forName("io.opentelemetry.context.Context", false, loader);
            Class<?> contextKeyClass = Class.forName("io.opentelemetry.context.ContextKey", false, loader);
            Class<?> scopeClass = Class.forName("io.opentelemetry.context.Scope", false, loader);
            Class<?> baggageClass = Class.forName("io.opentelemetry.api.baggage.Baggage", false, loader);
            Class<?> baggageBuilderClass = Class.forName(
                    "io.opentelemetry.api.baggage.BaggageBuilder", false, loader);
            contextKey = contextKeyClass.getMethod("named", String.class)
                    .invoke(null, "latticehub.traffic.context");
            currentMethod = contextClass.getMethod("current");
            getMethod = contextClass.getMethod("get", contextKeyClass);
            withMethod = contextClass.getMethod("with", contextKeyClass, Object.class);
            makeCurrentMethod = contextClass.getMethod("makeCurrent");
            closeMethod = scopeClass.getMethod("close");
            baggageCurrentMethod = baggageClass.getMethod("current");
            baggageToBuilderMethod = baggageClass.getMethod("toBuilder");
            baggageAsMapMethod = baggageClass.getMethod("asMap");
            baggageGetEntryValueMethod = baggageClass.getMethod("getEntryValue", String.class);
            baggageStoreInContextMethod = baggageClass.getMethod("storeInContext", contextClass);
            baggageBuilderRemoveMethod = baggageBuilderClass.getMethod("remove", String.class);
            baggageBuilderPutMethod = baggageBuilderClass.getMethod("put", String.class, String.class);
            baggageBuilderBuildMethod = baggageBuilderClass.getMethod("build");
        }

        @Override
        public Optional<TrafficContext> current() {
            Object value = invoke(getMethod, invoke(currentMethod, null), contextKey);
            if (value instanceof TrafficContext context) {
                return Optional.of(context);
            }
            if (value == CLEARED_CONTEXT) {
                return Optional.empty();
            }
            return restoreBaggage(invoke(baggageCurrentMethod, null));
        }

        @Override
        public Scope attach(TrafficContext context) {
            Object currentContext = invoke(currentMethod, null);
            Object currentBaggage = invoke(baggageCurrentMethod, null);
            Object baggageBuilder = invoke(baggageToBuilderMethod, currentBaggage);
            removeTrafficBaggage(currentBaggage, baggageBuilder);
            if (context != null && context.hasLabels()) {
                invoke(baggageBuilderPutMethod, baggageBuilder, TrafficContextBaggage.VERSION, "1");
                context.getCampaign().ifPresent(value -> invoke(
                        baggageBuilderPutMethod, baggageBuilder, TrafficContextBaggage.CAMPAIGN, value));
                context.getLane().ifPresent(value -> invoke(
                        baggageBuilderPutMethod, baggageBuilder, TrafficContextBaggage.LANE, value));
                context.getBucket().ifPresent(value -> invoke(
                        baggageBuilderPutMethod, baggageBuilder, TrafficContextBaggage.BUCKET, value.toString()));
            }
            Object baggage = invoke(baggageBuilderBuildMethod, baggageBuilder);
            Object attachedContext = invoke(baggageStoreInContextMethod, baggage, currentContext);
            attachedContext = invoke(
                    withMethod,
                    attachedContext,
                    contextKey,
                    context == null ? CLEARED_CONTEXT : context);
            Object attached = invoke(makeCurrentMethod, attachedContext);
            return new Scope() {
                private boolean closed;

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        invoke(closeMethod, attached);
                    }
                }
            };
        }

        private void removeTrafficBaggage(Object baggage, Object baggageBuilder) {
            for (String key : baggageKeys(baggage)) {
                if (key.startsWith("latticehub.traffic.")) {
                    invoke(baggageBuilderRemoveMethod, baggageBuilder, key);
                }
            }
        }

        private Optional<TrafficContext> restoreBaggage(Object baggage) {
            for (String key : baggageKeys(baggage)) {
                if (key.startsWith("latticehub.traffic.") && !isKnownTrafficBaggage(key)) {
                    return Optional.empty();
                }
            }
            String version = baggageValue(baggage, TrafficContextBaggage.VERSION);
            String campaign = baggageValue(baggage, TrafficContextBaggage.CAMPAIGN);
            String lane = baggageValue(baggage, TrafficContextBaggage.LANE);
            String rawBucket = baggageValue(baggage, TrafficContextBaggage.BUCKET);
            if (version == null && campaign == null && lane == null && rawBucket == null) {
                return Optional.empty();
            }
            if (!"1".equals(version) || !isCanonicalDecimal(rawBucket)) {
                return Optional.empty();
            }
            try {
                Integer bucket = rawBucket == null ? null : Integer.valueOf(rawBucket);
                return Optional.of(TrafficContext.builder()
                        .campaign(campaign)
                        .lane(lane)
                        .bucket(bucket)
                        .build());
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        private String baggageValue(Object baggage, String key) {
            Object value = invoke(baggageGetEntryValueMethod, baggage, key);
            return value instanceof String text ? text : null;
        }

        private Iterable<String> baggageKeys(Object baggage) {
            Object entries = invoke(baggageAsMapMethod, baggage);
            if (!(entries instanceof Map<?, ?> map)) {
                throw new IllegalStateException("OpenTelemetry baggage bridge returned an invalid map");
            }
            return map.keySet().stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        private static boolean isKnownTrafficBaggage(String key) {
            return TrafficContextBaggage.VERSION.equals(key)
                    || TrafficContextBaggage.CAMPAIGN.equals(key)
                    || TrafficContextBaggage.LANE.equals(key)
                    || TrafficContextBaggage.BUCKET.equals(key);
        }

        private static boolean isCanonicalDecimal(String value) {
            if (value == null) {
                return true;
            }
            if (value.isEmpty() || value.length() > 1 && value.charAt(0) == '0') {
                return false;
            }
            for (int index = 0; index < value.length(); index++) {
                if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                    return false;
                }
            }
            return true;
        }

        private static Object invoke(Method method, Object receiver, Object... arguments) {
            try {
                return method.invoke(receiver, arguments);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("OpenTelemetry context bridge failed", exception);
            }
        }
    }
}
