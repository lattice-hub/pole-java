package io.pole.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TargetEnvelopeConformanceTest {
    private static final Path CONTRACT_DIRECTORY = Path.of("contract");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void executesEveryValidSdkVector() throws IOException {
        JsonNode contract = readContract();
        assertEquals(9, contract.required("valid").size());

        for (JsonNode vector : contract.required("valid")) {
            String name = vector.required("name").asText();
            TargetEnvelope envelope = buildEnvelope(vector.required("input"));

            assertEquals(vector.required("normalized"), normalizedEnvelope(envelope), name);

            LinkedHashMap<String, String> baseHeaders = headerMap(vector.path("base_headers"));
            Map<String, String> encoded = TargetEnvelopeHttpHeaders.encode(baseHeaders, envelope);
            assertEquals(headerPairs(vector.required("expected_headers")), headerPairs(encoded), name);
        }
    }

    @Test
    void rejectsEveryInvalidSdkVector() throws IOException {
        JsonNode contract = readContract();
        assertEquals(20, contract.required("invalid").size());

        for (JsonNode vector : contract.required("invalid")) {
            assertThrows(
                    RuntimeException.class,
                    () -> buildEnvelope(vector.required("input")),
                    vector.required("name").asText());
        }
    }

    @Test
    void rejectsEveryLanguageSpecificInvalidVector() throws IOException {
        JsonNode contract = readContract();
        assertEquals(2, contract.required("language_specific_invalid").size());

        for (JsonNode vector : contract.required("language_specific_invalid")) {
            char[] codeUnits = new char[vector.required("utf16_code_units").size()];
            for (int index = 0; index < codeUnits.length; index++) {
                codeUnits[index] = (char) Integer.parseInt(
                        vector.required("utf16_code_units").get(index).asText(), 16);
            }
            String invalid = new String(codeUnits);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TargetEnvelope.builder()
                            .namespace("default")
                            .service(invalid)
                            .build(),
                    vector.required("name").asText());
        }
    }

    @Test
    void validatesVendoredAssetsAndSidecarReceiveStructure() throws Exception {
        JsonNode contract = readContract();
        assertEquals("pole-target-envelope", contract.required("contract").asText());
        assertEquals("1.0.0", contract.required("contract_version").asText());
        assertEquals("1", contract.required("envelope_version").asText());

        JsonNode sidecarReceive = contract.required("sidecar_receive");
        assertEquals(2, sidecarReceive.required("valid").size());
        assertEquals(8, sidecarReceive.required("invalid").size());
        for (JsonNode vector : sidecarReceive.required("valid")) {
            assertTrue(vector.required("headers").isArray());
            assertTrue(vector.required("expected_envelope").isObject());
        }
        for (JsonNode vector : sidecarReceive.required("invalid")) {
            assertTrue(vector.required("headers").isArray());
            assertTrue(vector.required("diagnostic").isTextual());
        }

        assertTrue(OBJECT_MAPPER.readTree(CONTRACT_DIRECTORY.resolve("schema.json").toFile()).isObject());
        verifyChecksums();
    }

    private static JsonNode readContract() throws IOException {
        return OBJECT_MAPPER.readTree(CONTRACT_DIRECTORY.resolve("conformance.json").toFile());
    }

    private static TargetEnvelope buildEnvelope(JsonNode input) {
        TargetEnvelope.Builder builder = TargetEnvelope.builder()
                .namespace(text(input, "namespace"))
                .service(text(input, "service"));
        if (input.has("protocol")) {
            builder.protocol(text(input, "protocol"));
        }
        if (input.has("group")) {
            builder.group(text(input, "group"));
        }
        if (input.has("service_version")) {
            builder.serviceVersion(text(input, "service_version"));
        }
        if (input.has("method")) {
            builder.method(text(input, "method"));
        }
        if (input.has("original_endpoint")) {
            builder.originalEndpoint(text(input, "original_endpoint"));
        }
        return builder.build();
    }

    private static String text(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static ObjectNode normalizedEnvelope(TargetEnvelope envelope) {
        ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
        normalized.put("namespace", envelope.getNamespace());
        normalized.put("service", envelope.getService());
        putIfPresent(normalized, "protocol", envelope.getProtocol());
        putIfPresent(normalized, "group", envelope.getGroup());
        putIfPresent(normalized, "service_version", envelope.getServiceVersion());
        putIfPresent(normalized, "method", envelope.getMethod());
        putIfPresent(normalized, "original_endpoint", envelope.getOriginalEndpoint());
        return normalized;
    }

    private static void putIfPresent(ObjectNode node, String name, String value) {
        if (value != null) {
            node.put(name, value);
        }
    }

    private static LinkedHashMap<String, String> headerMap(JsonNode pairs) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (pairs.isArray()) {
            for (JsonNode pair : pairs) {
                headers.put(pair.get(0).asText(), pair.get(1).asText());
            }
        }
        return headers;
    }

    private static List<List<String>> headerPairs(JsonNode pairs) {
        List<List<String>> headers = new ArrayList<>();
        for (JsonNode pair : pairs) {
            headers.add(List.of(pair.get(0).asText(), pair.get(1).asText()));
        }
        return headers;
    }

    private static List<List<String>> headerPairs(Map<String, String> headers) {
        List<List<String>> pairs = new ArrayList<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            pairs.add(List.of(header.getKey(), header.getValue()));
        }
        return pairs;
    }

    private static void verifyChecksums()
            throws IOException, NoSuchAlgorithmException {
        List<String> lines = Files.readAllLines(
                CONTRACT_DIRECTORY.resolve("SHA256SUMS"), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        Set<String> expectedFiles = new HashSet<>(
                Set.of("schema.json", "conformance.json"));
        for (String line : lines) {
            String[] parts = line.split("  ", 2);
            assertEquals(2, parts.length);
            assertTrue(expectedFiles.remove(parts[1]), parts[1]);
            byte[] content = Files.readAllBytes(CONTRACT_DIRECTORY.resolve(parts[1]));
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
            assertEquals(parts[0], actual, parts[1]);
        }
        assertTrue(expectedFiles.isEmpty(), expectedFiles.toString());

        String version = Files.readString(
                CONTRACT_DIRECTORY.resolve("VERSION"), StandardCharsets.UTF_8);
        assertTrue(version.contains("contract=pole-target-envelope\n"));
        assertTrue(version.contains("version=1.0.0\n"));
        assertTrue(version.contains("tag=thin-sdk-contract-v1.0.0\n"));
        assertTrue(version.contains(
                "commit=f45b0396b4680fe588a93086ceb2934d3e157d04\n"));
    }
}
