package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TargetServiceMetadataTest {
    @Test
    void encodesCanonicalMetadata() {
        Map<String, String> metadata = TargetServiceMetadata.encode(TargetService.builder()
                .namespace("生产")
                .service("订单%,服务")
                .build());

        assertEquals(List.of(
                TargetServiceMetadata.NAMESPACE,
                TargetServiceMetadata.SERVICE), new ArrayList<>(metadata.keySet()));
        assertEquals("%E7%94%9F%E4%BA%A7", metadata.get(TargetServiceMetadata.NAMESPACE));
        assertEquals(
                "%E8%AE%A2%E5%8D%95%25%2C%E6%9C%8D%E5%8A%A1",
                metadata.get(TargetServiceMetadata.SERVICE));
    }

    @Test
    void replacesInternalMetadataCaseInsensitivelyAndPreservesBaseOrder() {
        Map<String, String> existing = new LinkedHashMap<>();
        existing.put("z-header", "last");
        existing.put("LatticeHub-Target-Service", "forged-service");
        existing.put("a-header", "first");

        Map<String, String> metadata = TargetServiceMetadata.encode(existing, TargetService.builder()
                .namespace("default")
                .service("orders")
                .build());

        assertEquals(List.of(
                "z-header",
                "a-header",
                TargetServiceMetadata.NAMESPACE,
                TargetServiceMetadata.SERVICE), new ArrayList<>(metadata.keySet()));
        assertFalse(metadata.containsKey("LatticeHub-Target-Service"));
        assertEquals("orders", metadata.get(TargetServiceMetadata.SERVICE));
    }

    @Test
    void returnsUnmodifiableMetadata() {
        Map<String, String> metadata = TargetServiceMetadata.encode(TargetService.builder()
                .namespace("default")
                .service("orders")
                .build());

        assertThrows(UnsupportedOperationException.class, () -> metadata.put("new", "value"));
    }
}
