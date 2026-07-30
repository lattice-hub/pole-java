package io.pole.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TargetEnvelopeHttpHeadersTest {
    @Test
    void encodesCompleteEnvelopeInContractOrder() {
        TargetEnvelope envelope = TargetEnvelope.builder()
                .namespace("default")
                .service("orders")
                .protocol("grpc")
                .group("checkout")
                .serviceVersion("v2")
                .method("CreateOrder")
                .originalEndpoint("orders.internal:8080")
                .build();

        Map<String, String> headers = TargetEnvelopeHttpHeaders.encode(envelope);

        assertEquals(List.of(
                TargetEnvelopeHttpHeaders.ENVELOPE_VERSION,
                TargetEnvelopeHttpHeaders.NAMESPACE,
                TargetEnvelopeHttpHeaders.SERVICE,
                TargetEnvelopeHttpHeaders.PROTOCOL,
                TargetEnvelopeHttpHeaders.GROUP,
                TargetEnvelopeHttpHeaders.SERVICE_VERSION,
                TargetEnvelopeHttpHeaders.METHOD,
                TargetEnvelopeHttpHeaders.ORIGINAL_ENDPOINT), new ArrayList<>(headers.keySet()));
        assertEquals(Map.of(
                TargetEnvelopeHttpHeaders.ENVELOPE_VERSION, "1",
                TargetEnvelopeHttpHeaders.NAMESPACE, "default",
                TargetEnvelopeHttpHeaders.SERVICE, "orders",
                TargetEnvelopeHttpHeaders.PROTOCOL, "grpc",
                TargetEnvelopeHttpHeaders.GROUP, "checkout",
                TargetEnvelopeHttpHeaders.SERVICE_VERSION, "v2",
                TargetEnvelopeHttpHeaders.METHOD, "CreateOrder",
                TargetEnvelopeHttpHeaders.ORIGINAL_ENDPOINT, "orders.internal:8080"), headers);
    }

    @Test
    void omitsEmptyOptionalHeaders() {
        Map<String, String> headers = TargetEnvelopeHttpHeaders.encode(TargetEnvelope.builder()
                .namespace("default")
                .service("orders")
                .build());

        assertEquals(List.of(
                TargetEnvelopeHttpHeaders.ENVELOPE_VERSION,
                TargetEnvelopeHttpHeaders.NAMESPACE,
                TargetEnvelopeHttpHeaders.SERVICE), new ArrayList<>(headers.keySet()));
    }

    @Test
    void replacesInternalHeadersCaseInsensitivelyAndPreservesBaseOrder() {
        Map<String, String> existing = new LinkedHashMap<>();
        existing.put("z-header", "last");
        existing.put("X-Pole-Target-Service", "forged-service");
        existing.put("x-pole-target-protocol", "forged-protocol");
        existing.put("a-header", "first");

        Map<String, String> headers = TargetEnvelopeHttpHeaders.encode(existing, TargetEnvelope.builder()
                .namespace("default")
                .service("orders")
                .build());

        assertEquals(List.of(
                "z-header",
                "a-header",
                TargetEnvelopeHttpHeaders.ENVELOPE_VERSION,
                TargetEnvelopeHttpHeaders.NAMESPACE,
                TargetEnvelopeHttpHeaders.SERVICE), new ArrayList<>(headers.keySet()));
        assertEquals("orders", headers.get(TargetEnvelopeHttpHeaders.SERVICE));
        assertEquals(false, headers.containsKey("X-Pole-Target-Service"));
        assertEquals(false, headers.containsKey(TargetEnvelopeHttpHeaders.PROTOCOL));
    }

    @Test
    void returnsUnmodifiableHeaders() {
        Map<String, String> headers = TargetEnvelopeHttpHeaders.encode(TargetEnvelope.builder()
                .namespace("default")
                .service("orders")
                .build());

        assertThrows(UnsupportedOperationException.class, () -> headers.put("new", "value"));
    }
}
