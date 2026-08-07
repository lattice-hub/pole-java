package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TargetServiceTest {
    @Test
    void normalizesUnicodeWhitespace() {
        TargetService target = TargetService.builder()
                .namespace("\u00a0 default \u3000")
                .service(" orders ")
                .build();

        assertEquals("default", target.getNamespace());
        assertEquals("orders", target.getService());
    }

    @Test
    void rejectsMissingOrBlankRequiredFields() {
        assertThrows(NullPointerException.class, () -> TargetService.builder()
                .service("orders")
                .build());
        assertThrows(IllegalArgumentException.class, () -> TargetService.builder()
                .namespace("")
                .service("orders")
                .build());
        assertThrows(IllegalArgumentException.class, () -> TargetService.builder()
                .namespace("default")
                .service("\u2003")
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"line\nbreak", "next\u0085line"})
    void rejectsUnicodeCcCharacters(String value) {
        assertThrows(IllegalArgumentException.class, () -> TargetService.builder()
                .namespace("default")
                .service(value)
                .build());
    }

    @Test
    void rejectsUnpairedSurrogates() {
        assertThrows(IllegalArgumentException.class, () -> TargetService.builder()
                .namespace("default")
                .service("orders\ud800")
                .build());
    }

    @Test
    void hasValueObjectEquality() {
        TargetService first = minimalTarget();
        TargetService second = minimalTarget();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private static TargetService minimalTarget() {
        return TargetService.builder()
                .namespace("default")
                .service("orders")
                .build();
    }
}
