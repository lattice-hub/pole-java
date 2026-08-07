package io.github.latticehub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PoleClientJavaModuleTest {
    @Test
    void exposesApiAndSidecarClientTypes() {
        TargetService target = TargetService.builder()
                .namespace("default")
                .service("orders")
                .build();

        assertEquals("orders", target.getService());
        assertEquals(SidecarProtocol.HTTP, SidecarProtocol.valueOf("HTTP"));
    }
}
