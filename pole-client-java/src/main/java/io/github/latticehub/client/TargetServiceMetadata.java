package io.github.latticehub.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TargetServiceMetadata {
    public static final String NAMESPACE = "latticehub-target-namespace";
    public static final String SERVICE = "latticehub-target-service";

    private static final Set<String> INTERNAL_KEYS = Set.of(NAMESPACE, SERVICE);

    private TargetServiceMetadata() {
    }

    public static Map<String, String> encode(TargetService targetService) {
        return encode(Map.of(), targetService);
    }

    public static Map<String, String> encode(
            Map<String, String> existingMetadata,
            TargetService targetService) {
        return encode(existingMetadata, targetService, null);
    }

    public static Map<String, String> encode(
            Map<String, String> existingMetadata,
            TargetService targetService,
            TrafficContext explicitTrafficContext) {
        Objects.requireNonNull(existingMetadata, "existingMetadata must not be null");
        Objects.requireNonNull(targetService, "targetService must not be null");

        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        List<Map.Entry<String, String>> externalMetadata = new ArrayList<>();
        Map<String, String> propagatedMetadata = TrafficContextBaggage.inject(existingMetadata, explicitTrafficContext);
        for (Map.Entry<String, String> entry : propagatedMetadata.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "metadata key must not be null");
            String value = Objects.requireNonNull(entry.getValue(), "metadata value must not be null");
            if (!INTERNAL_KEYS.contains(name.toLowerCase(Locale.ROOT))) {
                externalMetadata.add(Map.entry(name, value));
            }
        }
        externalMetadata.forEach(entry -> metadata.put(entry.getKey(), entry.getValue()));
        metadata.put(NAMESPACE, encodeValue(targetService.getNamespace()));
        metadata.put(SERVICE, encodeValue(targetService.getService()));
        return Collections.unmodifiableMap(metadata);
    }

    private static String encodeValue(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte current : bytes) {
            int unsigned = current & 0xFF;
            if (unsigned >= 0x20 && unsigned <= 0x7E && unsigned != '%' && unsigned != ',') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0x0F, 16)));
            }
        }
        return encoded.toString();
    }
}
