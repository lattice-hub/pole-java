package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrafficContextTest {
    @Test
    void clearRestoresPreviousContext() {
        TrafficContext previous = TrafficContext.builder().lane("stable").build();
        try (TrafficContext.Scope ignored = TrafficContext.attach(previous)) {
            try (TrafficContext.Scope cleared = TrafficContext.clear()) {
                assertTrue(TrafficContext.current().isEmpty());
            }
            assertEquals(previous, TrafficContext.current().orElseThrow());
        }
    }

    @AfterEach
    void resetStorage() {
        TrafficContext.reset();
    }

    @Test
    void scopeRestoresThePreviousNativeContext() {
        TrafficContext outer = TrafficContext.builder().campaign("checkout-v2").build();
        TrafficContext inner = TrafficContext.builder().lane("gray").bucket(42).build();

        assertTrue(TrafficContext.current().isEmpty());
        try (TrafficContext.Scope ignored = TrafficContext.attach(outer)) {
            assertEquals(outer, TrafficContext.current().orElseThrow());
            try (TrafficContext.Scope nested = TrafficContext.attach(inner)) {
                assertEquals(inner, TrafficContext.current().orElseThrow());
            }
            assertEquals(outer, TrafficContext.current().orElseThrow());
        }
        assertTrue(TrafficContext.current().isEmpty());
    }

    @Test
    void constructorValidationExposesStableDiagnosticCodes() {
        assertEquals(
                "INVALID_BUCKET",
                assertThrows(TrafficContextException.class, () -> TrafficContext.builder().bucket(10000).build())
                        .getCode());
        assertEquals(
                "LABEL_TOO_LARGE",
                assertThrows(
                                TrafficContextException.class,
                                () -> TrafficContext.builder().lane("灰".repeat(43)).build())
                        .getCode());
    }

    @Test
    void optionalOpenTelemetryBridgeWritesAndRecoversBaggage() {
        TrafficContext trafficContext = TrafficContext.builder()
                .campaign("checkout-v2")
                .lane("gray")
                .bucket(42)
                .build();

        assertTrue(TrafficContext.tryInstallOpenTelemetryBridge());
        Context seeded = Baggage.builder()
                .put("vendor.key", "preserved")
                .put("latticehub.traffic.future", "stale")
                .put(TrafficContextBaggage.LANE, "stale")
                .build()
                .storeInContext(Context.current());
        try (io.opentelemetry.context.Scope seedScope = seeded.makeCurrent()) {
            try (TrafficContext.Scope ignored = TrafficContext.attach(trafficContext)) {
                assertEquals(trafficContext, TrafficContext.current().orElseThrow());
                assertEquals("preserved", Baggage.current().getEntryValue("vendor.key"));
                assertEquals(null, Baggage.current().getEntryValue("latticehub.traffic.future"));
                assertEquals("1", Baggage.current().getEntryValue(TrafficContextBaggage.VERSION));
                assertEquals("checkout-v2", Baggage.current().getEntryValue(TrafficContextBaggage.CAMPAIGN));
                assertEquals("gray", Baggage.current().getEntryValue(TrafficContextBaggage.LANE));
                assertEquals("42", Baggage.current().getEntryValue(TrafficContextBaggage.BUCKET));

                Map<String, String> carrier = new LinkedHashMap<>();
                TextMapSetter<Map<String, String>> setter = Map::put;
                W3CBaggagePropagator.getInstance().inject(Context.current(), carrier, setter);
                assertEquals(
                        trafficContext,
                        TrafficContextBaggage.extract(List.of(carrier.get("baggage"))).orElseThrow());
            }
        }
        assertTrue(TrafficContext.current().isEmpty());

        Context recovered = Baggage.builder()
                .put(TrafficContextBaggage.VERSION, "1")
                .put(TrafficContextBaggage.LANE, "recovered")
                .build()
                .storeInContext(Context.current());
        try (io.opentelemetry.context.Scope ignored = recovered.makeCurrent()) {
            assertEquals(TrafficContext.builder().lane("recovered").build(), TrafficContext.current().orElseThrow());
        }

        Context invalid = Baggage.builder()
                .put(TrafficContextBaggage.LANE, "missing-version")
                .build()
                .storeInContext(Context.current());
        try (io.opentelemetry.context.Scope ignored = invalid.makeCurrent()) {
            assertTrue(TrafficContext.current().isEmpty());
        }

        Context unknownReserved = Baggage.builder()
                .put(TrafficContextBaggage.VERSION, "1")
                .put(TrafficContextBaggage.LANE, "gray")
                .put("latticehub.traffic.future", "unknown")
                .build()
                .storeInContext(Context.current());
        try (io.opentelemetry.context.Scope ignored = unknownReserved.makeCurrent()) {
            assertTrue(TrafficContext.current().isEmpty());
        }
    }

    @Test
    void wrappedExecutorAndCallableRestoreCapturedNativeContext() throws Exception {
        TrafficContext trafficContext = TrafficContext.builder().lane("gray").build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            try (TrafficContext.Scope ignored = TrafficContext.attach(trafficContext)) {
                Executor wrappedExecutor = TrafficContext.wrap(executorService);
                CompletableFuture<TrafficContext> future = CompletableFuture.supplyAsync(
                        () -> TrafficContext.current().orElseThrow(), wrappedExecutor);
                assertEquals(trafficContext, future.join());

                Callable<TrafficContext> callable = TrafficContext.wrap(
                        () -> TrafficContext.current().orElseThrow());
                assertEquals(trafficContext, executorService.submit(callable).get());
            }
            assertTrue(TrafficContext.current().isEmpty());
            assertTrue(executorService.submit(TrafficContext::current).get().isEmpty());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void wrappedTaskWithNoCapturedContextMasksAndRestoresNativeWorkerContext() throws Exception {
        TrafficContext stale = TrafficContext.builder().lane("stale-worker").build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<TrafficContext.Scope> workerScope = new AtomicReference<>();
            executorService.submit(() -> workerScope.set(TrafficContext.attach(stale))).get();

            Callable<Optional<TrafficContext>> wrapped = TrafficContext.wrap(TrafficContext::current);
            assertTrue(executorService.submit(wrapped).get().isEmpty());
            assertEquals(stale, executorService.submit(TrafficContext::current).get().orElseThrow());

            executorService.submit(() -> workerScope.get().close()).get();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void wrappedTaskWithNoCapturedContextMasksAndRestoresOtelWorkerContext() throws Exception {
        assertTrue(TrafficContext.tryInstallOpenTelemetryBridge());
        TrafficContext stale = TrafficContext.builder().lane("stale-worker").build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<TrafficContext.Scope> workerScope = new AtomicReference<>();
            executorService.submit(() -> workerScope.set(TrafficContext.attach(stale))).get();

            Callable<Optional<TrafficContext>> wrapped = TrafficContext.wrap(TrafficContext::current);
            assertTrue(executorService.submit(wrapped).get().isEmpty());
            assertEquals(stale, executorService.submit(TrafficContext::current).get().orElseThrow());

            executorService.submit(() -> workerScope.get().close()).get();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void injectsCanonicalBaggageAndPreservesExternalMembers() {
        TrafficContext context = TrafficContext.builder()
                .campaign("checkout v2")
                .lane("灰度")
                .bucket(7)
                .build();

        String baggage = TrafficContextBaggage.inject(
                        List.of("vendor=value;property=one,latticehub.traffic.lane=stale"), context)
                .orElseThrow();

        assertEquals(
                "vendor=value;property=one,latticehub.traffic.version=1,"
                        + "latticehub.traffic.campaign=checkout%20v2,"
                        + "latticehub.traffic.lane=%E7%81%B0%E5%BA%A6,"
                        + "latticehub.traffic.bucket=7",
                baggage);
    }

    @Test
    void extractsOnlyCanonicalReservedMembers() {
        TrafficContext context = TrafficContextBaggage.extract(List.of(
                        "vendor=value,latticehub.traffic.version=1,"
                                + "latticehub.traffic.campaign=checkout-v2,"
                                + "latticehub.traffic.bucket=42"))
                .orElseThrow();

        assertEquals("checkout-v2", context.getCampaign().orElseThrow());
        assertEquals(42, context.getBucket().orElseThrow());
        assertTrue(context.getLane().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TrafficContextBaggage.extract(List.of(
                "latticehub.traffic.version=1,latticehub.traffic.lane=%67ray")));
    }

    @Test
    void removesBaggageCarrierWhenOnlyOldReservedMembersRemain() {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put("Baggage", "latticehub.traffic.version=1,latticehub.traffic.lane=stale");
        carrier.put("x-request-id", "42");

        Map<String, String> injected = TrafficContextBaggage.inject(carrier);

        assertFalse(injected.keySet().stream().anyMatch(name -> name.equalsIgnoreCase("baggage")));
        assertEquals("42", injected.get("x-request-id"));
    }

    @Test
    void acceptsW3cOwsAndEmptyExternalValuesAcrossHeaders() {
        TrafficContext context = TrafficContextBaggage.extract(List.of(
                        " vendor = ; property = one \t, latticehub.traffic.version = 1 ",
                        "\tlatticehub.traffic.lane = gray"))
                .orElseThrow();

        assertEquals("gray", context.getLane().orElseThrow());
        assertEquals(
                "vendor = ; property = one,latticehub.traffic.version=1,latticehub.traffic.lane=gray",
                TrafficContextBaggage.inject(List.of(" vendor = ; property = one "), context).orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> TrafficContextBaggage.inject(List.of("vendor=bad\\value"), context));
        assertThrows(IllegalArgumentException.class,
                () -> TrafficContextBaggage.extract(List.of("vendor=" + "a".repeat(8192))));
    }

    @Test
    void acceptsVersionOnlyAndKeepsCaseDistinctForeignMembers() {
        TrafficContext empty = TrafficContextBaggage.extract(List.of("latticehub.traffic.version=1"))
                .orElseThrow();

        assertFalse(empty.hasLabels());
        assertEquals(
                "LatticeHub.traffic.lane=foreign",
                TrafficContextBaggage.inject(List.of(" LatticeHub.traffic.lane=foreign "), empty).orElseThrow());
    }

    @Test
    void emptyContextCleansEveryReservedPrefixMember() {
        TrafficContext empty = TrafficContext.builder().build();

        assertEquals(
                "vendor=value",
                TrafficContextBaggage.inject(
                                List.of("vendor=value", "latticehub.traffic.version=1,latticehub.traffic.future=stale"),
                                empty)
                        .orElseThrow());
    }
}
