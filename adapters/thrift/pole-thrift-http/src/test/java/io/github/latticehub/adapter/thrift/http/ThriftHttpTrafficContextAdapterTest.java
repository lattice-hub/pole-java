package io.github.latticehub.adapter.thrift.http;

import io.github.latticehub.client.TrafficContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThriftHttpTrafficContextAdapterTest {
    @AfterEach
    void resetContext() {
        TrafficContext.reset();
    }

    @Test
    void injectsClientAndRestoresServerContext() throws Exception {
        FakeClient client = new FakeClient();
        client.setCustomHeader("baggage", "vendor=value");
        TrafficContext expected = TrafficContext.builder().campaign("canary").build();
        try (TrafficContext.Scope ignored = TrafficContext.attach(expected)) {
            ThriftHttpTrafficContextAdapter.injectClient(client);
        }

        assertTrue(client.customHeaders.get("baggage").contains("vendor=value"));
        FakeRequest request = new FakeRequest(client.customHeaders.get("baggage"));
        try (AutoCloseable ignored = ThriftHttpTrafficContextAdapter.enterServer(request)) {
            assertEquals(expected, TrafficContext.current().orElseThrow());
        }
        assertTrue(TrafficContext.current().isEmpty());
    }

    public static final class FakeClient {
        private final Map<String, String> customHeaders = new HashMap<>();

        public void setCustomHeader(String name, String value) {
            customHeaders.put(name, value);
        }
    }

    public static final class FakeRequest {
        private final String baggage;

        private FakeRequest(String baggage) {
            this.baggage = baggage;
        }

        public java.util.Enumeration<String> getHeaders(String name) {
            return "baggage".equalsIgnoreCase(name)
                    ? Collections.enumeration(java.util.List.of(baggage))
                    : Collections.emptyEnumeration();
        }
    }
}
