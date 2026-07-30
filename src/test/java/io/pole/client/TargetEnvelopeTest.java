package io.pole.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TargetEnvelopeTest {
    @Test
    void normalizesUnicodeWhitespace() {
        TargetEnvelope envelope = TargetEnvelope.builder()
                .namespace("\u00a0 default \u3000")
                .service(" orders ")
                .protocol(" ")
                .build();

        assertEquals("default", envelope.getNamespace());
        assertEquals("orders", envelope.getService());
        assertNull(envelope.getProtocol());
    }

    @Test
    void rejectsMissingOrBlankRequiredFields() {
        assertThrows(NullPointerException.class, () -> TargetEnvelope.builder()
                .service("orders")
                .build());
        assertThrows(IllegalArgumentException.class, () -> TargetEnvelope.builder()
                .namespace("")
                .service("orders")
                .build());
        assertThrows(IllegalArgumentException.class, () -> TargetEnvelope.builder()
                .namespace("default")
                .service("\u2003")
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"line\nbreak", "next\u0085line"})
    void rejectsUnicodeCcCharacters(String value) {
        assertThrows(IllegalArgumentException.class, () -> TargetEnvelope.builder()
                .namespace("default")
                .service(value)
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "orders.internal:8080",
            "127.0.0.1:8080",
            "[::1]:8080",
            "[2001:db8::1]:1",
            "[::ffff:192.0.2.1]:65535",
            "[0:0:0:0:0:ffff:192.0.2.1]:8080"
    })
    void acceptsValidOriginalEndpoints(String endpoint) {
        TargetEnvelope envelope = minimalBuilder()
                .originalEndpoint(endpoint)
                .build();

        assertEquals(endpoint, envelope.getOriginalEndpoint());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "orders.internal",
            "orders.internal:0",
            "orders.internal:65536",
            "orders.internal:http",
            "2001:db8::1:8080",
            "orders.internal:",
            ":8080",
            "[::1]",
            "[::1]:0",
            "[:::1]:8080",
            "[::ffff:１９２.0.2.1]:80",
            "[::ffff:١٩٢.0.2.1]:80"
    })
    void rejectsInvalidOriginalEndpoints(String endpoint) {
        assertThrows(IllegalArgumentException.class, () -> minimalBuilder()
                .originalEndpoint(endpoint)
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "orders\u2003internal:8080",
            "orders/internal:8080",
            "orders\\internal:8080",
            "orders[internal:8080",
            "orders]internal:8080",
            "orders@internal:8080",
            "orders?internal:8080",
            "orders#internal:8080"
    })
    void rejectsForbiddenHostCharacters(String endpoint) {
        assertThrows(IllegalArgumentException.class, () -> minimalBuilder()
                .originalEndpoint(endpoint)
                .build());
    }

    @Test
    void exposesDefaultSidecarEndpoint() {
        assertEquals("http://127.0.0.1:15001", PoleClientDefaults.SIDECAR_ENDPOINT);
    }

    @Test
    void hasValueObjectEquality() {
        TargetEnvelope first = minimalBuilder()
                .protocol("grpc")
                .build();
        TargetEnvelope second = minimalBuilder()
                .protocol("grpc")
                .build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private static TargetEnvelope.Builder minimalBuilder() {
        return TargetEnvelope.builder()
                .namespace("default")
                .service("orders");
    }
}
