package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TargetServiceConformanceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void passesVendoredValidVectors() throws IOException {
        JsonNode vectors = readVectors();
        for (JsonNode vector : vectors.get("valid")) {
            JsonNode input = vector.get("input");
            TargetService target = TargetService.builder()
                    .namespace(input.get("namespace").textValue())
                    .service(input.get("service").textValue())
                    .build();

            assertEquals(vector.get("normalized").get("namespace").textValue(), target.getNamespace());
            assertEquals(vector.get("normalized").get("service").textValue(), target.getService());
            assertEquals(expectedEntries(vector), new ArrayList<>(
                    TargetServiceMetadata.encode(target).entrySet()));
        }
    }

    @Test
    void rejectsVendoredInvalidVectors() throws IOException {
        for (JsonNode vector : readVectors().get("invalid")) {
            JsonNode input = vector.get("input");
            assertThrows(RuntimeException.class, () -> TargetService.builder()
                    .namespace(input.get("namespace").textValue())
                    .service(input.get("service").textValue())
                    .build(), vector.get("name").textValue());
        }
    }

    private static JsonNode readVectors() throws IOException {
        return OBJECT_MAPPER.readTree(Files.readString(Path.of("contract/conformance.json")));
    }

    private static List<Map.Entry<String, String>> expectedEntries(JsonNode vector) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (JsonNode entry : vector.get("expected_metadata")) {
            entries.add(Map.entry(entry.get(0).textValue(), entry.get(1).textValue()));
        }
        return entries;
    }
}
