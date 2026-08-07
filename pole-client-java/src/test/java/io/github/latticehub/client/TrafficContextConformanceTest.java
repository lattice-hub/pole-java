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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrafficContextConformanceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void passesAllVendoredInjectVectors() throws IOException {
        for (JsonNode vector : readVectors().get("valid")) {
            TrafficContext context = contextFrom(vector.get("input").get("labels"));
            Optional<String> actual = TrafficContextBaggage.inject(strings(vector.get("existing_baggage")), context);
            assertEquals(vector.get("expected_baggage").textValue(), actual.orElse(null), vector.get("name").textValue());
        }
    }

    @Test
    void passesAllVendoredReceiveVectors() throws IOException {
        JsonNode receive = readVectors().get("sidecar_receive");
        for (JsonNode vector : receive.get("valid")) {
            TrafficContext actual = TrafficContextBaggage.extract(strings(vector.get("baggage"))).orElseThrow();
            assertEquals(contextFrom(vector.get("expected").get("labels")), actual, vector.get("name").textValue());
        }
        for (JsonNode vector : receive.get("invalid")) {
            TrafficContextException error = assertThrows(
                    TrafficContextException.class,
                    () -> TrafficContextBaggage.extract(strings(vector.get("baggage"))),
                    vector.get("name").textValue());
            assertEquals(vector.get("diagnostic").textValue(), error.getCode(), vector.get("name").textValue());
        }
    }

    private static JsonNode readVectors() throws IOException {
        return OBJECT_MAPPER.readTree(Files.readString(Path.of("contract/traffic-context/v1/conformance.json")));
    }

    private static TrafficContext contextFrom(JsonNode labels) {
        TrafficContext.Builder builder = TrafficContext.builder();
        if (labels.has("campaign")) {
            builder.campaign(labels.get("campaign").textValue());
        }
        if (labels.has("lane")) {
            builder.lane(labels.get("lane").textValue());
        }
        if (labels.has("bucket")) {
            builder.bucket(labels.get("bucket").intValue());
        }
        return builder.build();
    }

    private static List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.textValue()));
        return result;
    }

}
