package io.github.latticehub.agent.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoleAgentBridgeTest {
    @AfterEach
    void clearCallbacks() {
        PoleAgentBridge.clear();
    }

    @Test
    void dispatchesToRegisteredCallback() {
        AtomicReference<Object> received = new AtomicReference<>();
        PoleAgentBridge.register("test", received::set);

        PoleAgentBridge.dispatch("test", "value");

        assertEquals("value", received.get());
    }

    @Test
    void rejectsDuplicateAndMissingCallbacks() {
        PoleAgentBridge.register("test", ignored -> { });

        assertThrows(IllegalStateException.class, () -> PoleAgentBridge.register("test", ignored -> { }));
        assertThrows(IllegalStateException.class, () -> PoleAgentBridge.dispatch("missing", new Object()));
    }
}
