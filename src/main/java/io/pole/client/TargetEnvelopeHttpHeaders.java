package io.pole.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TargetEnvelopeHttpHeaders {
    public static final String ENVELOPE_VERSION = "x-pole-target-envelope-version";
    public static final String NAMESPACE = "x-pole-target-namespace";
    public static final String SERVICE = "x-pole-target-service";
    public static final String PROTOCOL = "x-pole-target-protocol";
    public static final String GROUP = "x-pole-target-group";
    public static final String SERVICE_VERSION = "x-pole-target-service-version";
    public static final String METHOD = "x-pole-target-method";
    public static final String ORIGINAL_ENDPOINT = "x-pole-original-endpoint";

    private static final Set<String> INTERNAL_HEADERS = Set.of(
            ENVELOPE_VERSION,
            NAMESPACE,
            SERVICE,
            PROTOCOL,
            GROUP,
            SERVICE_VERSION,
            METHOD,
            ORIGINAL_ENDPOINT);

    private TargetEnvelopeHttpHeaders() {
    }

    public static Map<String, String> encode(TargetEnvelope envelope) {
        return encode(Map.of(), envelope);
    }

    public static Map<String, String> encode(
            Map<String, String> existingHeaders,
            TargetEnvelope envelope) {
        Objects.requireNonNull(existingHeaders, "existingHeaders must not be null");
        Objects.requireNonNull(envelope, "envelope must not be null");

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        List<Map.Entry<String, String>> externalHeaders = new ArrayList<>();
        for (Map.Entry<String, String> entry : existingHeaders.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "header name must not be null");
            String value = Objects.requireNonNull(entry.getValue(), "header value must not be null");
            if (!INTERNAL_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                externalHeaders.add(Map.entry(name, value));
            }
        }
        externalHeaders.forEach(entry -> headers.put(entry.getKey(), entry.getValue()));

        headers.put(ENVELOPE_VERSION, TargetEnvelope.VERSION);
        headers.put(NAMESPACE, encodeValue(envelope.getNamespace()));
        headers.put(SERVICE, encodeValue(envelope.getService()));
        putIfPresent(headers, PROTOCOL, envelope.getProtocol());
        putIfPresent(headers, GROUP, envelope.getGroup());
        putIfPresent(headers, SERVICE_VERSION, envelope.getServiceVersion());
        putIfPresent(headers, METHOD, envelope.getMethod());
        putIfPresent(headers, ORIGINAL_ENDPOINT, envelope.getOriginalEndpoint());
        return Collections.unmodifiableMap(headers);
    }

    private static void putIfPresent(Map<String, String> headers, String name, String value) {
        if (value != null) {
            headers.put(name, encodeValue(value));
        }
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
